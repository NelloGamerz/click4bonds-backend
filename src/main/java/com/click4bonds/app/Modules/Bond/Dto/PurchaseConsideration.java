package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.PurchasePriceTreatment;

/**
 * The purchase leg of a YTM projection, together with the settlement convention
 * that produced it.
 *
 * <p>
 * Carrying the treatment and the upcoming coupon's dates on the result means the
 * caller can log and test <em>why</em> a given consideration was used, instead
 * of only seeing the amount.
 *
 * @param treatment              cum-interest or ex-interest
 * @param cleanPrice             the bond's quoted clean price
 * @param accruedInterestCharged the accrued interest component actually included
 *                               in the consideration; zero for an ex-interest
 *                               purchase
 * @param purchaseConsideration  what the buyer pays, before being negated for
 *                               the XIRR series
 * @param upcomingPaymentDate    payment date of the next coupon, or {@code null}
 *                               when the projection has no future coupon
 * @param upcomingRecordDate     that coupon's record date, or {@code null} when
 *                               the bond has no record-date rule
 */
public record PurchaseConsideration(
        PurchasePriceTreatment treatment,
        BigDecimal cleanPrice,
        BigDecimal accruedInterestCharged,
        BigDecimal purchaseConsideration,
        LocalDate upcomingPaymentDate,
        LocalDate upcomingRecordDate
) {

    /**
     * @return the negative purchase cash flow ready for the XIRR series
     */
    public BigDecimal purchaseCashFlow() {
        return purchaseConsideration.negate();
    }

    public boolean isExInterest() {
        return treatment == PurchasePriceTreatment.EX_INTEREST;
    }
}
