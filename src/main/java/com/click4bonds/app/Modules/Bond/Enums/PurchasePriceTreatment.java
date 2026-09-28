package com.click4bonds.app.Modules.Bond.Enums;

/**
 * How a purchase settles against the upcoming coupon's record date.
 *
 * <p>
 * This is a settlement/pricing convention, which is a different question from
 * coupon entitlement. Entitlement answers "does the buyer receive this coupon?";
 * the treatment answers "what consideration does the buyer pay?".
 *
 * <p>
 * The two always move together on a record-date-driven bond: a buyer who is not
 * entitled to the upcoming coupon must not be charged the accrued interest that
 * accrued on that coupon, because that accrued amount is the seller's
 * compensation for keeping it.
 */
public enum PurchasePriceTreatment {

    /**
     * Cum-interest: the buyer receives the upcoming coupon, so the seller is
     * compensated for the interest already accrued on it.
     *
     * <pre>
     * purchase consideration = clean price + accrued interest
     * </pre>
     */
    CUM_INTEREST,

    /**
     * Ex-interest: the upcoming coupon belongs to the seller, so there is
     * nothing to compensate the seller for.
     *
     * <pre>
     * purchase consideration = clean price
     * </pre>
     *
     * <p>
     * The accrued interest reported by {@code AccruedInterestService} is exactly
     * the accrued portion of that excluded coupon, so charging it would pay the
     * seller twice: once through the coupon and again through the price.
     */
    EX_INTEREST
}
