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
     * Ex-interest: the upcoming coupon belongs to the seller, so the buyer is
     * owed the part of it covering the stretch from settlement to the coupon
     * date.
     *
     * <pre>
     * purchase consideration = clean price + (negative accrued interest)
     *                        = clean price - |accrued interest|
     * </pre>
     *
     * <p>
     * That accrual is reported negative by {@code AccruedInterestService}, which
     * is what makes this treatment the sign of the accrual rather than a
     * separate adjustment: charging the positive accrual would charge the buyer
     * for a coupon the seller keeps.
     */
    EX_INTEREST
}
