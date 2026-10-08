package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealAccrual;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
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
    private final BondRepository bondRepository;
    private final UserService userService;
    private final DealReferenceGenerator dealReferenceGenerator;
    private final DealConfirmationMapper mapper;
    private final DealAccrualCalculator accrualCalculator;
    private final DocumentProperties documentProperties;

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

        PurchaseContext context = prepare(userId, request);

        Bond bond = context.bond();

        /*
         * THE oversell guard. One conditional UPDATE, evaluated by PostgreSQL
         * while it holds the row lock: if another buyer got there first and took
         * the remaining units, this matches zero rows.
         */
        int reserved = bondRepository.reserveQuantity(bond.getId(), context.totalQuantity());

        if (reserved == 0) {

            log.warn(
                    "Inventory reservation rejected: isin={} requested={} (insufficient remaining quantity)",
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
        Bond reservedBond = bondRepository.findById(bond.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + context.isin()));

        markSoldOutIfExhausted(reservedBond);

        log.info(
                "Inventory reserved: isin={} quantity={} remaining={}",
                context.isin(),
                context.totalQuantity(),
                reservedBond.getRemainingQuantity());

        DealConfirmation deal = buildDeal(
                context.customer(),
                reservedBond,
                request,
                context.quantityPerLot(),
                context.totalQuantity(),
                idempotencyKey,
                dealReferenceGenerator.next(context.dealDate()));

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
                mapper.toDocumentData(saved, context.valueDate(), accrual));
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

        PurchaseContext context = prepare(userId, request);

        /*
         * Measured against the bond as loaded rather than against a post-
         * reservation reload, because there is no reservation. The accrual reads
         * the coupon schedule and the price, neither of which the reservation
         * touches, so this is the same figure {@link #create} would compute.
         */
        DealAccrual accrual = accrualCalculator
                .calculate(context.bond(), context.valueDate())
                .orElse(null);

        DealConfirmation projected = buildDeal(
                context.customer(),
                context.bond(),
                request,
                context.quantityPerLot(),
                context.totalQuantity(),
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
                mapper.toDocumentData(projected, context.valueDate(), accrual));
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
    private PurchaseContext prepare(
            UUID userId,
            CreateDealConfirmationRequest request) {

        String isin = normalizeIsin(request.isin());

        /*
         * Captured once. The reference, the letter's deal date and the value
         * date the accrual is measured to all have to agree, and a request
         * arriving at midnight would otherwise see two different "today"s.
         */
        LocalDate dealDate = LocalDate.now();

        LocalDate valueDate = dealDate.plusDays(
                documentProperties.getDeal().getValueDateOffsetDays());

        /*
         * Loaded first so a request from an unknown or disabled account fails
         * before any inventory is touched. This entity is referenced, never
         * modified, so it does not matter that the reservation below detaches it.
         */
        User customer = getPurchasableCustomer(userId);

        Bond bond = bondRepository.findByIsin(isin)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + isin));

        /*
         * The units in a lot are a property of the bond, not of the request, so
         * they are read from the row just loaded and only the number of lots
         * comes from the caller. This has to happen after the bond is read, which
         * is why the total is no longer computed before any database access.
         */
        long quantityPerLot = resolveLotSize(bond);

        long totalQuantity = calculateTotalQuantity(
                quantityPerLot,
                request.numberOfLots());

        log.info(
                "Deal request: isin={} quantityPerLot={} numberOfLots={} totalQuantity={}",
                isin,
                quantityPerLot,
                request.numberOfLots(),
                totalQuantity);

        validatePurchasable(bond, totalQuantity);

        return new PurchaseContext(
                isin,
                customer,
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
         * NULL inventory is not zero inventory: it means nobody ever recorded how
         * many units of this bond exist. Treating it as unlimited would sell
         * units that do not exist, so it is refused with an explanation an admin
         * can act on.
         */
        if (bond.getRemainingQuantity() == null) {
            throw new BadRequestException(
                    "Inventory has not been set for bond: " + bond.getIsin());
        }

        if (bond.getRemainingQuantity() <= 0) {
            throw new ConflictException(
                    "Insufficient quantity available for bond: " + bond.getIsin());
        }

        /*
         * Checked here as well as in the UPDATE so the common "asked for more
         * than exists" case gets a message that says how much is left, instead
         * of the bare rejected-reservation one.
         */
        if (bond.getRemainingQuantity() < totalQuantity) {
            throw new ConflictException(
                    "Insufficient quantity available for bond: " + bond.getIsin()
                            + " (requested " + totalQuantity
                            + ", available " + bond.getRemainingQuantity() + ")");
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
            User customer,
            Bond bond,
            CreateDealConfirmationRequest request,
            long quantityPerLot,
            long totalQuantity,
            String idempotencyKey,
            String dealReference) {

        BigDecimal pricePerUnit = bond.getPrice();

        return DealConfirmation.builder()
                .dealReference(dealReference)
                .customer(customer)
                .bond(bond)
                .isin(bond.getIsin())
                /*
                 * The lot size the deal was actually struck at, resolved from the
                 * bond above — stored rather than left to be re-derived, because
                 * an admin editing the bond later must not restate an old deal.
                 */
                .quantityPerLot(quantityPerLot)
                .numberOfLots(request.numberOfLots())
                .totalQuantity(totalQuantity)
                .pricePerUnit(pricePerUnit)
                /*
                 * Null price stays null rather than becoming zero: the bond's
                 * price is genuinely unknown, and a zero total would read as a
                 * free purchase.
                 */
                .totalAmount(pricePerUnit == null
                        ? null
                        : pricePerUnit.multiply(BigDecimal.valueOf(totalQuantity)))
                .idempotencyKey(idempotencyKey)
                .build();
    }

    /**
     * Flips the bond to {@code SOLD_OUT} when its last unit is taken.
     *
     * <p>Cosmetic, not a safety mechanism: the reservation is already correct
     * whether or not the status changes, and a bond restocked through
     * {@code UpdateBondRequest} is reactivated explicitly by an admin.</p>
     */
    private void markSoldOutIfExhausted(Bond bond) {

        if (bond.getRemainingQuantity() != null
                && bond.getRemainingQuantity() == 0
                && bond.getStatus() == BondStatus.ACTIVE) {

            bond.setStatus(BondStatus.SOLD_OUT);

            bondRepository.save(bond);
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
