package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * Everything the deal confirmation document needs, in one flat record.
 *
 * <p>This is the seam for the Excel template step: the template is filled from
 * this record, and the template code never sees an entity, a repository or a
 * session. It is assembled inside the transaction that creates the deal, so the
 * document step — which runs after that transaction has committed — never
 * touches a lazy association.</p>
 *
 * <p>Values are snapshots taken at purchase time; they do not follow later edits
 * to the bond.</p>
 *
 * <p><strong>Why the interest figures are carried rather than recomputed.</strong>
 * {@code AccruedInterestService} takes a {@code Bond} entity, and the document
 * step may not load one. Recomputing the arithmetic here instead would create a
 * second definition of accrued interest that could drift from the one used for
 * pricing, so the results travel — see {@link DealAccrual}.</p>
 */
public record DealConfirmationDocumentData(

        String dealReference,

        LocalDate dealDate,

        Instant dealCreatedAt,

        /** Settlement date the letter prints and the accrual is measured to. */
        LocalDate valueDate,

        String customerName,

        String customerEmail,

        String bondName,

        String isin,

        /** {@code SecurityType} name, or null when the bond has none. */
        String securityType,

        BigDecimal couponRate,

        LocalDate maturityDate,

        /** The bond's raw interest-payment description, e.g. "23rd of every month". */
        String ipDateDescription,

        /** Coupon date on or before the value date; null when unresolved. */
        LocalDate previousCouponDate,

        /** Days from {@code previousCouponDate} to {@code valueDate}; null when unresolved. */
        Long accruedDays,

        /** Accrued interest for one bond of face value 100; null when unresolved. */
        BigDecimal accruedInterestPerHundredFace,

        Long quantityPerLot,

        Long numberOfLots,

        Long totalQuantity,

        BigDecimal pricePerUnit,

        BigDecimal totalAmount,

        DealConfirmationStatus status
) {

    /**
     * Builds the snapshot from a deal.
     *
     * <p>Must be called while the persistence context is still open — it reads
     * {@code customer} and {@code bond}, both of which are lazily loaded.</p>
     *
     * @param deal      the deal just persisted
     * @param valueDate settlement date, computed once by the caller so the date
     *                  the letter prints and the date the accrual was measured
     *                  to cannot drift apart
     * @param accrual   interest figures computed in this transaction, or null
     *                  when they could not be determined. Null is a normal
     *                  outcome, not an error: the document step declines to
     *                  generate a letter rather than printing blanks.
     */
    public static DealConfirmationDocumentData from(
            DealConfirmation deal,
            LocalDate valueDate,
            DealAccrual accrual) {

        var bond = deal.getBond();

        return new DealConfirmationDocumentData(
                deal.getDealReference(),
                deal.getCreatedAt() == null
                        ? LocalDate.now()
                        : LocalDate.ofInstant(deal.getCreatedAt(), ZoneOffset.UTC),
                deal.getCreatedAt(),
                valueDate,
                customerName(deal.getCustomer()),
                deal.getCustomer() == null ? null : deal.getCustomer().getEmail(),
                bond == null ? null : bond.getName(),
                deal.getIsin(),
                bond == null || bond.getSecurityType() == null
                        ? null
                        : bond.getSecurityType().name(),
                bond == null ? null : bond.getCouponRate(),
                bond == null ? null : bond.getMaturityDate(),
                bond == null ? null : bond.getIpDateDescription(),
                accrual == null ? null : accrual.previousCouponDate(),
                accrual == null ? null : accrual.accruedDays(),
                accrual == null ? null : accrual.accruedInterestPerHundredFace(),
                deal.getQuantityPerLot(),
                deal.getNumberOfLots(),
                deal.getTotalQuantity(),
                deal.getPricePerUnit(),
                deal.getTotalAmount(),
                deal.getStatus());
    }

    private static String customerName(User customer) {

        if (customer == null) {
            return null;
        }

        String name = ((customer.getFirstName() == null ? "" : customer.getFirstName())
                + " "
                + (customer.getLastName() == null ? "" : customer.getLastName())).trim();

        /*
         * A user whose profile has no name yet is still a real counterparty, so
         * fall back to the identifier the customer knows: their email.
         */
        return name.isEmpty() ? customer.getEmail() : name;
    }
}
