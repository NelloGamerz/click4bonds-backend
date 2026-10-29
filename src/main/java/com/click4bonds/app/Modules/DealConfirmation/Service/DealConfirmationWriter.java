package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CounterpartyDetails;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealAccrual;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Party.Enums.Depository;
import com.click4bonds.app.Modules.Party.Model.BankAccount;
import com.click4bonds.app.Modules.Party.Model.DematAccount;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.BankAccountRepository;
import com.click4bonds.app.Modules.Party.Repository.DematAccountRepository;
import com.click4bonds.app.Modules.Party.Service.PanDetailsService;
import com.click4bonds.app.Modules.Party.Service.SecretCipher;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.Client;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.ClientRepository;
import com.click4bonds.app.Modules.User.Service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates a deal confirmation and reserves its inventory — atomically.
 *
 * <p>This class exists separately from {@link DealConfirmationService} for one
 * reason: the reservation and the deal row must be written in a single
 * transaction, while the service around it also does things that must NOT be in
 * that transaction (the document step, and recovering from a duplicate request).
 * Keeping the transactional half in its own bean means Spring's proxy actually
 * applies, which a self-invocation inside one class would silently bypass.</p>
 *
 * <p><strong>Ordering.</strong> The inventory is reserved by a single
 * conditional UPDATE before the deal is inserted. If the insert then fails, the
 * transaction rolls back and the units return to the bond. The reverse order
 * would risk a deal that exists with no units behind it.</p>
 *
 * <p><strong>{@link #preview} is the read-only twin of {@link #create}.</strong>
 * It validates a request through the same {@code prepare} step and computes the
 * same figures, but reserves nothing, issues no reference and writes no row, so
 * a caller that is only showing the deal cannot leave the database changed. The
 * two live together because they must agree: a preview that accepted something
 * the purchase refuses would show a customer a deal they cannot have.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationWriter {

    private final DealConfirmationRepository dealConfirmationRepository;
    private final BondService bondService;
    private final UserService userService;
    private final ClientRepository clientRepository;
    private final DealReferenceGenerator dealReferenceGenerator;
    private final DealConfirmationMapper mapper;
    private final DealAccrualCalculator accrualCalculator;
    private final DocumentProperties documentProperties;
    private final BankAccountRepository bankAccountRepository;
    private final DematAccountRepository dematAccountRepository;
    private final PanDetailsService panDetailsService;
    private final SecretCipher secretCipher;

    /** A created deal, paired with the snapshot its document will be built from. */
    public record CreatedDeal(
            DealConfirmationResponse response,
            DealConfirmationDocumentData documentData) {
    }

    /**
     * Everything a request resolves to before anything is written.
     *
     * <p>Exists so that {@link #create} and {@link #preview} agree on what a
     * request means: a preview that validated the bond, the lot size or the
     * quantities differently from a purchase would show figures the purchase
     * would refuse to honour. Only the write half differs between the two.</p>
     */
    private record PurchaseContext(
            String isin,
            User customer,
            Client client,
            Bond bond,
            long quantityPerLot,
            long totalQuantity,
            LocalDate dealDate,
            LocalDate valueDate) {
    }

    /**
     * Validates the request, reserves the units and persists the deal.
     *
     * @param userId  authenticated customer, from the JWT
     * @param request      requested bond and quantities
     * @param idempotencyKey optional client key, stored on the deal so a retry
     *                        can be recognised; may be null
     * @return the persisted deal
     * @throws ResourceNotFoundException bond or customer does not exist
     * @throws BadRequestException       bond is not purchasable, has no
     *                                   inventory configured, or the quantities
     *                                   overflow
     * @throws ConflictException         the bond does not have enough units left
     */
    @Transactional
    public CreatedDeal create(
            UUID userId,
            CreateDealConfirmationRequest request,
            String idempotencyKey) {

        User customer = getPurchasableCustomer(userId);

        Bond bond = bondService.findBond(normalizeIsin(request.isin()));

        PurchaseContext context = prepare(
                customer,
                null,
                bond,
                request.numberOfLots(),
                LocalDate.now());

        return commit(
                context,
                request.numberOfLots(),
                idempotencyKey,
                null,
                request.bankAccountId(),
                request.dematAccountId(),
                null,
                null,
                DealSide.SELL,
                null);
    }

    /**
     * Creates a deal on a client's behalf, from the back office.
     *
     * <p>Same validation, reservation and letter as a customer purchase; the
     * difference is the counterparty (a {@link Client}, not an account) and that
     * the employee may choose the deal date. A null date means today.</p>
     *
     * @param idempotencyKey may be null; a back-office deal never sends one
     * @param employeeId the staff account creating the deal
     * @param bankAccountId the client's chosen bank account, or null
     * @param dematAccountId the client's chosen demat account, or null
     * @param deliveryMode  how the deal settles; decides the letter's blocks
     * @param settlementNumber the clearing house's number, supplied by the
     *                         employee for ICCL and NSCCL
     * @param side whether we are selling to or buying from the client
     * @param depository the depository chosen for a demat buy, or null
     * @throws ResourceNotFoundException client, bond, or a chosen account does
     *                                   not exist for that client
     * @throws BadRequestException       bond is not purchasable, or the
     *                                   quantities overflow
     * @throws ConflictException         the bond does not have enough units left
     */
    @Transactional
    public CreatedDeal createForClient(
            UUID clientId,
            UUID bondId,
            Long numberOfLots,
            LocalDate dealDate,
            String idempotencyKey,
            UUID employeeId,
            UUID bankAccountId,
            UUID dematAccountId,
            DeliveryMode deliveryMode,
            String settlementNumber,
            DealSide side,
            Depository depository) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + clientId));

        User createdBy = userService.getUser(employeeId);

        Bond bond = bondService.findBondById(bondId);

        PurchaseContext context = prepare(
                null,
                client,
                bond,
                numberOfLots,
                dealDate == null ? LocalDate.now() : dealDate);

        return commit(
                context,
                numberOfLots,
                idempotencyKey,
                createdBy,
                bankAccountId,
                dematAccountId,
                deliveryMode,
                settlementNumber,
                side,
                depository);
    }

    /**
     * Reserves the units and writes the deal. Shared by both create paths so a
     * back-office deal cannot diverge from a customer purchase in what it
     * reserves or what it stores.
     */
    private CreatedDeal commit(
            PurchaseContext context,
            Long numberOfLots,
            String idempotencyKey,
            User createdBy,
            UUID bankAccountId,
            UUID dematAccountId,
            DeliveryMode deliveryMode,
            String settlementNumber,
            DealSide side,
            Depository depository) {

        Bond bond = context.bond();

        /*
         * Resolved before anything is reserved: an account that does not belong
         * to this counterparty is a bad request, not a reason to take units and
         * put them back.
         */
        CounterpartyDetails counterparty =
                counterpartyDetails(context, bankAccountId, dematAccountId);

        /*
         * THE oversell guard. One conditional UPDATE, evaluated by PostgreSQL
         * while it holds the row lock: if another buyer got there first and took
         * the remaining units, this matches zero rows.
         */
        int reserved = bondService.reserveQuantity(
                bond.getId(),
                BigDecimal.valueOf(context.totalQuantity()));

        if (reserved == 0) {

            log.warn(
                    "Inventory reservation rejected: isin={} requested={} (insufficient quantum)",
                    context.isin(),
                    context.totalQuantity());

            throw new ConflictException(
                    "Insufficient quantity available for bond: " + context.isin());
        }

        /*
         * The UPDATE went straight to the database, so the bond in memory is
         * stale — it still holds the pre-reservation figure. The repository
         * clears the persistence context for exactly this reason; reload to get
         * the post-reservation value.
         */
        Bond reservedBond = bondService.findBondById(bond.getId());

        markSoldOutIfExhausted(reservedBond);

        log.info(
                "Inventory reserved: isin={} quantity={} remaining quantum={}",
                context.isin(),
                context.totalQuantity(),
                reservedBond.getQuantum());

        DealConfirmation deal = buildDeal(
                context,
                numberOfLots,
                idempotencyKey,
                dealReferenceGenerator.next(context.dealDate(), side),
                createdBy);

        DealConfirmation saved = dealConfirmationRepository.save(deal);

        log.info(
                "Deal created: reference={} isin={} totalQuantity={}",
                saved.getDealReference(),
                saved.getIsin(),
                saved.getTotalQuantity());

        /*
         * Computed here, in the transaction, because the services behind it take
         * the Bond entity and the document step that consumes the result runs
         * after this transaction has committed. An empty result is normal — the
         * document step declines to generate a letter rather than printing a
         * blank interest figure.
         */
        DealAccrual accrual = accrualCalculator
                .calculate(reservedBond, context.valueDate())
                .orElse(null);

        /*
         * Mapped while the session is open. These two DTOs are the only things
         * that leave the transaction, so the caller can log and document the deal
         * after it has committed without touching a lazy association.
         */
        return new CreatedDeal(
                mapper.toResponse(saved),
                mapper.toDocumentData(
                        saved,
                        context.valueDate(),
                        accrual,
                        counterparty,
                        deliveryMode,
                        settlementNumber,
                        side,
                        depository));
    }

    /**
     * Answers what the deal would be, writing nothing.
     *
     * <p>For a caller that is showing the deal rather than filing it: the request
     * is validated exactly as a purchase would be and the figures are computed
     * from the bond as it stands, but no units are reserved, no reference is
     * issued and no row is written. Nothing here can leave the database changed,
     * which is why it is {@code readOnly} rather than merely refraining from
     * saving — the setting is what makes that a property of the transaction
     * rather than a promise about this method body.</p>
     *
     * <p><strong>The deal it describes has no reference and no id</strong>, and
     * that is not a gap to fill: a reference is issued from the database by
     * {@link DealReferenceGenerator}, so producing one here would mean writing
     * the sequence row — and a reference allocated for a page that was only being
     * looked at would be a number no deal ever holds. The response carries them
     * as null, which is what "this deal does not exist yet" honestly looks
     * like.</p>
     *
     * @param userId  authenticated customer, from the JWT
     * @param request requested bond and quantities
     * @return the figures the deal would carry, with nothing persisted
     * @throws ResourceNotFoundException bond or customer does not exist
     * @throws BadRequestException       bond is not purchasable, or the
     *                                   quantities overflow
     * @throws ConflictException         the bond does not have enough units left
     */
    @Transactional(readOnly = true)
    public CreatedDeal preview(
            UUID userId,
            CreateDealConfirmationRequest request) {

        User customer = getPurchasableCustomer(userId);

        Bond bond = bondService.findBond(normalizeIsin(request.isin()));

        PurchaseContext context = prepare(
                customer,
                null,
                bond,
                request.numberOfLots(),
                LocalDate.now());

        /*
         * Measured against the bond as loaded rather than against a post-
         * reservation reload, because there is no reservation. The accrual reads
         * the coupon schedule and the price, neither of which the reservation
         * touches, so this is the same figure {@link #create} would compute.
         */
        DealAccrual accrual = accrualCalculator
                .calculate(context.bond(), context.valueDate())
                .orElse(null);

        CounterpartyDetails counterparty = counterpartyDetails(
                context,
                request.bankAccountId(),
                request.dematAccountId());

        DealConfirmation projected = buildDeal(
                context,
                request.numberOfLots(),
                null,
                null,
                null);

        /*
         * The entity's builder defaults the status to CREATED, which is true of a
         * row that was written and false of one that was not. Cleared so the
         * response cannot report a deal in a state no deal is in.
         */
        projected.setStatus(null);

        log.info(
                "View-only deal request: isin={} numberOfLots={} totalQuantity={}"
                        + " (nothing was written)",
                context.isin(),
                request.numberOfLots(),
                context.totalQuantity());

        return new CreatedDeal(
                mapper.toResponse(projected),
                mapper.toDocumentData(
                        projected,
                        context.valueDate(),
                        accrual,
                        counterparty,
                        null,
                        null,
                        DealSide.SELL,
                        null));
    }

    // =========================================================
    // THE REQUEST, BEFORE ANYTHING IS WRITTEN
    // =========================================================

    /**
     * Resolves a request into the customer, the bond and the quantities, and
     * refuses it if a purchase of it would be refused.
     *
     * <p>Reads only — the same method serves {@link #create}, which goes on to
     * reserve and write, and {@link #preview}, which stops here. Duplicating the
     * validation for the preview would let the two drift, and a preview that
     * accepted something the purchase rejects is worse than no preview: it shows
     * a customer figures for a deal they cannot have.</p>
     *
     * @throws ResourceNotFoundException bond or customer does not exist
     * @throws BadRequestException       bond is not purchasable or the quantities
     *                                   overflow
     * @throws ConflictException         the bond does not have enough units left
     */
    /**
     * Resolves a request into the (already loaded) counterparty and bond, and the
     * quantities and dates of the deal, refusing anything a purchase would refuse.
     *
     * <p>Reads only — the same method serves {@link #create}, {@link #createForClient}
     * and {@link #preview}. Duplicating the validation would let them drift, and a
     * preview that accepted something the purchase rejects would show figures for
     * a deal that cannot be made.</p>
     *
     * @param customer the buying account, or null for a back-office deal
     * @param client   the client the deal is for, or null for a customer deal
     * @param dealDate the date the deal is struck, which also fixes the value date
     *                 and the reference's month
     * @throws BadRequestException       bond is not purchasable or the quantities
     *                                   overflow
     * @throws ConflictException         the bond does not have enough units left
     */
    private PurchaseContext prepare(
            User customer,
            Client client,
            Bond bond,
            Long numberOfLots,
            LocalDate dealDate) {

        String isin = normalizeIsin(bond.getIsin());

        LocalDate valueDate = dealDate.plusDays(
                documentProperties.getDeal().getValueDateOffsetDays());

        /*
         * The units in a lot are a property of the bond, not of the request, so
         * they are read from the row loaded above and only the number of lots
         * comes from the caller.
         */
        long quantityPerLot = resolveLotSize(bond);

        long totalQuantity = calculateTotalQuantity(
                quantityPerLot,
                numberOfLots);

        log.info(
                "Deal request: isin={} quantityPerLot={} numberOfLots={} totalQuantity={}",
                isin,
                quantityPerLot,
                numberOfLots,
                totalQuantity);

        validatePurchasable(bond, totalQuantity);

        return new PurchaseContext(
                isin,
                customer,
                client,
                bond,
                quantityPerLot,
                totalQuantity,
                dealDate,
                valueDate);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private User getPurchasableCustomer(UUID userId) {

        User customer = userService.getUser(userId);

        if (customer.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException(
                    "This account cannot place deals");
        }

        return customer;
    }

    /**
     * @throws BadRequestException when the bond cannot be bought at all, as
     *                             opposed to not having enough units left
     */
    private void validatePurchasable(Bond bond, long totalQuantity) {

        if (bond.getStatus() != BondStatus.ACTIVE) {
            throw new BadRequestException(
                    "Bond is not available for purchase: " + bond.getIsin()
                            + " (status " + bond.getStatus() + ")");
        }

        /*
         * NULL quantum is not zero quantum: it means nobody ever recorded how
         * much of this bond exists. Treating it as unlimited would sell
         * units that do not exist, so it is refused with an explanation an admin
         * can act on.
         */
        if (bond.getQuantum() == null) {
            throw new BadRequestException(
                    "Inventory has not been set for bond: " + bond.getIsin());
        }

        if (bond.getQuantum().signum() <= 0) {
            throw new ConflictException(
                    "Insufficient quantity available for bond: " + bond.getIsin());
        }

        /*
         * Checked here as well as in the UPDATE so the common "asked for more
         * than exists" case gets a message that says how much is left, instead
         * of the bare rejected-reservation one.
         */
        BigDecimal requested = BigDecimal.valueOf(totalQuantity);

        if (bond.getQuantum().compareTo(requested) < 0) {
            throw new ConflictException(
                    "Insufficient quantity available for bond: " + bond.getIsin()
                            + " (requested " + totalQuantity
                            + ", available " + bond.getQuantum() + ")");
        }
    }

    /**
     * Reads the bond's lot size as a whole number of units.
     *
     * <p>The lot size is stored as a {@code BigDecimal}, so it can hold a
     * fraction, but a quantity of units cannot. Anything under half a unit
     * rounds away and anything from half a unit up rounds up, matching the
     * {@code HALF_UP} convention the rest of this codebase rounds money with.</p>
     *
     * @throws BadRequestException when the bond has no lot size, or one that is
     *                             not a positive whole number of units. Both are
     *                             data problems an admin has to fix: guessing a
     *                             quantity would reserve units the bond does not
     *                             trade in, and the deal would be wrong in a way
     *                             nothing downstream could detect.
     */
    private long resolveLotSize(Bond bond) {

        BigDecimal lotSize = bond.getLotSize();

        if (lotSize == null) {
            throw new BadRequestException(
                    "Lot size has not been set for bond: " + bond.getIsin());
        }

        long units;

        try {
            units = lotSize.setScale(0, RoundingMode.HALF_UP).longValueExact();

        } catch (ArithmeticException unusable) {

            throw new BadRequestException(
                    "Lot size is not a usable quantity for bond: " + bond.getIsin()
                            + " (lot size " + lotSize + ")");
        }

        if (units <= 0) {
            throw new BadRequestException(
                    "Lot size must be greater than 0 for bond: " + bond.getIsin()
                            + " (lot size " + lotSize + ")");
        }

        return units;
    }

    /**
     * @throws BadRequestException when the product of the two quantities does not
     *                             fit in a {@code long}
     */
    private long calculateTotalQuantity(long quantityPerLot, Long numberOfLots) {

        try {
            return Math.multiplyExact(quantityPerLot, numberOfLots);

        } catch (ArithmeticException overflow) {

            /*
             * Both factors are positive (enforced on the request), so an
             * overflow here is an absurd order rather than a malformed one. It
             * must not wrap around into a small number and reserve the wrong
             * quantity.
             */
            throw new BadRequestException(
                    "Total quantity is too large: "
                            + quantityPerLot + " x " + numberOfLots);
        }
    }

    // =========================================================
    // PERSISTENCE
    // =========================================================

    /**
     * @param dealReference the reference this deal is being filed under, or null
     *                      for a preview. Deliberately a parameter rather than
     *                      something this method asks the generator for: issuing
     *                      one writes to the sequence table, and a preview must
     *                      not.
     */
    private DealConfirmation buildDeal(
            PurchaseContext context,
            Long numberOfLots,
            String idempotencyKey,
            String dealReference,
            User createdBy) {

        Bond bond = context.bond();

        BigDecimal pricePerUnit = bond.getPrice();

        return DealConfirmation.builder()
                .dealReference(dealReference)
                .customer(context.customer())
                .client(context.client())
                .createdBy(createdBy)
                /*
                 * Only a back-office deal records the date it was struck. A
                 * customer deal keeps its creation date, which is the date its
                 * letter has always printed.
                 */
                .dealDate(context.client() == null ? null : context.dealDate())
                .bond(bond)
                .isin(bond.getIsin())
                /*
                 * The lot size the deal was actually struck at, resolved from the
                 * bond above — stored rather than left to be re-derived, because
                 * an admin editing the bond later must not restate an old deal.
                 */
                .quantityPerLot(context.quantityPerLot())
                .numberOfLots(numberOfLots)
                .totalQuantity(context.totalQuantity())
                .pricePerUnit(pricePerUnit)
                /*
                 * Null price stays null rather than becoming zero: the bond's
                 * price is genuinely unknown, and a zero total would read as a
                 * free purchase.
                 */
                .totalAmount(pricePerUnit == null
                        ? null
                        : pricePerUnit.multiply(BigDecimal.valueOf(context.totalQuantity())))
                .idempotencyKey(idempotencyKey)
                .build();
    }

    /**
     * The counterparty's party particulars for the letter.
     *
     * <p>The party is the client's for a back-office deal and the customer's
     * profile's for a purchase. A chosen account that does not belong to that
     * party is refused rather than silently ignored: the caller asked for a
     * specific account, and printing a different one on a contract would be
     * worse than failing.</p>
     *
     * @return null when the counterparty has no party, so no particulars
     */
    private CounterpartyDetails counterpartyDetails(
            PurchaseContext context,
            UUID bankAccountId,
            UUID dematAccountId) {

        Party party = partyOf(context);

        if (party == null) {

            if (bankAccountId != null || dematAccountId != null) {
                throw new ResourceNotFoundException(
                        "The counterparty has no party, so no account can belong to it");
            }

            return null;
        }

        DematAccount demat = dematAccountId == null
                ? null
                : dematAccountRepository.findByIdAndParty(dematAccountId, party)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Demat account not found for this deal: " + dematAccountId));

        BankAccount bank = bankAccountId == null
                ? null
                : bankAccountRepository.findByIdAndParty(bankAccountId, party)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Bank account not found for this deal: " + bankAccountId));

        return new CounterpartyDetails(
                panDetailsService.plainPan(party).orElse(null),
                demat == null ? null : demat.getAccountHolderName(),
                demat == null ? null : demat.getDpId(),
                demat == null ? null : demat.getClientId(),
                bank == null ? null : bank.getBankName(),
                party.getAddress(),
                bank == null ? null : bank.getBranchLocation(),
                bank == null ? null : bank.getIfscCode(),
                bank == null ? null : secretCipher.decrypt(bank.getAccountNumber()));
    }

    private Party partyOf(PurchaseContext context) {

        if (context.customer() != null) {
            return context.customer().getCustomerProfile() == null
                    ? null
                    : context.customer().getCustomerProfile().getParty();
        }

        return context.client() == null ? null : context.client().getParty();
    }

    /**
     * Flips the bond to {@code SOLD_OUT} when its last unit is taken.
     *
     * <p>Cosmetic, not a safety mechanism: the reservation is already correct
     * whether or not the status changes, and a bond restocked through
     * {@code UpdateBondRequest} is reactivated explicitly by an admin.</p>
     */
    private void markSoldOutIfExhausted(Bond bond) {

        if (bond.getQuantum() != null
                && bond.getQuantum().signum() == 0
                && bond.getStatus() == BondStatus.ACTIVE) {

            bond.setStatus(BondStatus.SOLD_OUT);

            bondService.saveBond(bond);
        }
    }

    /**
     * Matches {@code BondService}: ISINs are stored upper-cased, so a lower-case
     * request still resolves.
     */
    private String normalizeIsin(String isin) {
        return isin == null ? null : isin.trim().toUpperCase();
    }
}
