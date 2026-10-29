package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.Party.Enums.Depository;
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

        /**
         * The bond's credit rating as stored, e.g. {@code "Sovereign"} for a
         * government security, or null when the bond has none.
         *
         * <p>Carried so the document step can choose a layout without loading the
         * bond it may no longer reach. It is a snapshot like everything else here:
         * editing the bond afterwards does not re-letter an existing deal.</p>
         */
        String rating,

        /** The bond's semi-annual yield as a percentage, or null when not computed. */
        BigDecimal semiYtm,

        /** The bond's annual yield as a percentage, or null when not computed. */
        BigDecimal annualYtm,

        /** The bond's coupon as stored, a percentage: 8.80 means 8.80%. */
        BigDecimal couponRate,

        LocalDate maturityDate,

        /** The bond's raw interest-payment description, e.g. "23rd of every month". */
        String ipDateDescription,

        /** The bond's raw put/call description, or null when it has none. */
        String putCallDescription,

        /** Coupon date on or before the value date; null when unresolved. */
        LocalDate previousCouponDate,

        /** Days from {@code previousCouponDate} to {@code valueDate}; null when unresolved. */
        Long accruedDays,

        /** Accrued interest for one bond of face value 100; null when unresolved. */
        BigDecimal accruedInterestPerHundredFace,

        /**
         * Clean price of one lot at the value date, in rupees, from the same
         * purchase consideration the YTM projection uses; null when unresolved.
         */
        BigDecimal principalPerLot,

        Long quantityPerLot,

        Long numberOfLots,

        Long totalQuantity,

        BigDecimal pricePerUnit,

        BigDecimal totalAmount,

        DealConfirmationStatus status,

        /**
         * The counterparty's PAN, demat and bank particulars as they stood when
         * the deal was struck, or null when none could be resolved.
         */
        CounterpartyDetails counterparty,

        /** How a back-office deal settles; null on a customer-placed deal. */
        DeliveryMode deliveryMode,

        /** The employee-supplied settlement number; null on a customer deal. */
        String settlementNumber,

        /**
         * Which side of the trade we are on. {@code SELL} (our sale) for a
         * customer deal and for a back-office sale; {@code BUY} (our purchase)
         * when the employee is buying from the client.
         */
        DealSide side,

        /**
         * The depository whose demat identifiers we print on a buy; null unless
         * the employee chose one.
         */
        Depository depository
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
     * @param counterparty the chosen party particulars, or null
     * @param deliveryMode how a back-office deal settles, or null
     * @param settlementNumber the employee-supplied settlement number, or null
     * @param side which side of the trade we are on
     * @param depository the depository chosen for a demat buy, or null
     */
    public static DealConfirmationDocumentData from(
            DealConfirmation deal,
            LocalDate valueDate,
            DealAccrual accrual,
            CounterpartyDetails counterparty,
            DeliveryMode deliveryMode,
            String settlementNumber,
            DealSide side,
            Depository depository) {

        var bond = deal.getBond();

        return new DealConfirmationDocumentData(
                deal.getDealReference(),
                deal.getDealDate() != null
                        ? deal.getDealDate()
                        : deal.getCreatedAt() == null
                                ? LocalDate.now()
                                : LocalDate.ofInstant(deal.getCreatedAt(), ZoneOffset.UTC),
                deal.getCreatedAt(),
                valueDate,
                counterpartyName(deal),
                counterpartyEmail(deal),
                bond == null ? null : bond.getName(),
                deal.getIsin(),
                bond == null || bond.getSecurityType() == null
                        ? null
                        : bond.getSecurityType().name(),
                bond == null ? null : bond.getRating(),
                bond == null ? null : bond.getSemiYtm(),
                bond == null ? null : bond.getAnnualYtm(),
                bond == null ? null : bond.getCouponRate(),
                bond == null ? null : bond.getMaturityDate(),
                bond == null ? null : bond.getIpDateDescription(),
                bond == null ? null : bond.getPutCallDescription(),
                accrual == null ? null : accrual.previousCouponDate(),
                accrual == null ? null : accrual.accruedDays(),
                accrual == null ? null : accrual.accruedInterestPerHundredFace(),
                accrual == null ? null : accrual.principalPerLot(),
                deal.getQuantityPerLot(),
                deal.getNumberOfLots(),
                deal.getTotalQuantity(),
                deal.getPricePerUnit(),
                deal.getTotalAmount(),
                deal.getStatus(),
                counterparty,
                deliveryMode,
                settlementNumber,
                side,
                depository);
    }

    /**
     * The letter's counterparty is the buying account for a customer deal and
     * the client record for a back-office deal; exactly one of them is set.
     */
    private static String counterpartyName(DealConfirmation deal) {

        if (deal.getCustomer() != null) {
            return customerName(deal.getCustomer());
        }

        return deal.getClient() == null ? null : deal.getClient().getName();
    }

    private static String counterpartyEmail(DealConfirmation deal) {

        if (deal.getCustomer() != null) {
            return deal.getCustomer().getEmail();
        }

        return deal.getClient() == null ? null : deal.getClient().getEmail();
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
