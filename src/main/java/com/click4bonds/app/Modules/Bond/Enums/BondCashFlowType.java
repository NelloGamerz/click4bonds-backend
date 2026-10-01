package com.click4bonds.app.Modules.Bond.Enums;

/**
 * What a single date in a bond's projected cash flow represents.
 *
 * <p>
 * A date can carry more than one movement — an amortizing bond may pay a coupon
 * and repay principal on the same day — hence {@link #COUPON_AND_PRINCIPAL}.
 */
public enum BondCashFlowType {
    PURCHASE,
    COUPON,
    PRINCIPAL,
    COUPON_AND_PRINCIPAL
}
