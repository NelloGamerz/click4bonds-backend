package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
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
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
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

    /** A created deal, paired with the snapshot its document will be built from. */
    public record CreatedDeal(
            DealConfirmationResponse response,
            DealConfirmationDocumentData documentData) {
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

        String isin = normalizeIsin(request.isin());

        long totalQuantity = calculateTotalQuantity(
                request.quantityPerLot(),
                request.numberOfLots());

        log.info(
                "Deal creation attempt: isin={} quantityPerLot={} numberOfLots={} totalQuantity={}",
                isin,
                request.quantityPerLot(),
                request.numberOfLots(),
                totalQuantity);

        /*
         * Loaded first so a request from an unknown or disabled account fails
         * before any inventory is touched. This entity is referenced, never
         * modified, so it does not matter that the reservation below detaches it.
         */
        User customer = getPurchasableCustomer(userId);

        Bond bond = bondRepository.findByIsin(isin)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + isin));

        validatePurchasable(bond, totalQuantity);

        /*
         * THE oversell guard. One conditional UPDATE, evaluated by PostgreSQL
         * while it holds the row lock: if another buyer got there first and took
         * the remaining units, this matches zero rows.
         */
        int reserved = bondRepository.reserveQuantity(bond.getId(), totalQuantity);

        if (reserved == 0) {

            log.warn(
                    "Inventory reservation rejected: isin={} requested={} (insufficient remaining quantity)",
                    isin,
                    totalQuantity);

            throw new ConflictException(
                    "Insufficient quantity available for bond: " + isin);
        }

        /*
         * The UPDATE went straight to the database, so the bond in memory is
         * stale — it still holds the pre-reservation figure. The repository
         * clears the persistence context for exactly this reason; reload to get
         * the post-reservation value.
         */
        Bond reservedBond = bondRepository.findById(bond.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + isin));

        markSoldOutIfExhausted(reservedBond);

        log.info(
                "Inventory reserved: isin={} quantity={} remaining={}",
                isin,
                totalQuantity,
                reservedBond.getRemainingQuantity());

        DealConfirmation deal = buildDeal(
                customer,
                reservedBond,
                request,
                totalQuantity,
                idempotencyKey);

        DealConfirmation saved = dealConfirmationRepository.save(deal);

        log.info(
                "Deal created: reference={} isin={} totalQuantity={}",
                saved.getDealReference(),
                saved.getIsin(),
                saved.getTotalQuantity());

        /*
         * Mapped while the session is open. These two DTOs are the only things
         * that leave the transaction, so the caller can log and document the deal
         * after it has committed without touching a lazy association.
         */
        return new CreatedDeal(
                mapper.toResponse(saved),
                mapper.toDocumentData(saved));
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
     * @throws BadRequestException when the product of the two quantities does not
     *                             fit in a {@code long}
     */
    private long calculateTotalQuantity(Long quantityPerLot, Long numberOfLots) {

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

    private DealConfirmation buildDeal(
            User customer,
            Bond bond,
            CreateDealConfirmationRequest request,
            long totalQuantity,
            String idempotencyKey) {

        BigDecimal pricePerUnit = bond.getPrice();

        return DealConfirmation.builder()
                .dealReference(dealReferenceGenerator.next(LocalDate.now()))
                .customer(customer)
                .bond(bond)
                .isin(bond.getIsin())
                .quantityPerLot(request.quantityPerLot())
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
