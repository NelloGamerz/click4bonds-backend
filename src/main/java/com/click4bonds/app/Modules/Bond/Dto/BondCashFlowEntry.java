package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.BondCashFlowType;

/**
 * One dated movement in a bond's projected cash flow.
 *
 * <p>
 * {@code amount} is the signed total for the date: negative for the purchase
 * leg (money paid out), positive for coupons and principal (money received).
 *
 * @param date                 date the money moves
 * @param type                 what the movement is
 * @param couponAmount         coupon component, or {@code null} when the date
 *                             carries no coupon
 * @param principalAmount      principal component, or {@code null} when the date
 *                             carries no principal repayment
 * @param amount               signed total for the date
 * @param outstandingPrincipal principal still outstanding at that date, or
 *                             {@code null} for the purchase leg
 */
public record BondCashFlowEntry(
        LocalDate date,
        BondCashFlowType type,
        BigDecimal couponAmount,
        BigDecimal principalAmount,
        BigDecimal amount,
        BigDecimal outstandingPrincipal
) {
}
