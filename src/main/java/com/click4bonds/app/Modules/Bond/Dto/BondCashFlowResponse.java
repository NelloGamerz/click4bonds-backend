package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The full projected cash flow of a single bond as of a calculation date.
 *
 * <p>
 * The series is the same one {@link com.click4bonds.app.Modules.Bond.Service.YtmCalculationService}
 * discounts, so the schedule and the published YTM always reconcile.
 *
 * @param isin                  the bond's ISIN
 * @param bondName              the bond's name
 * @param calculationDate       the date the projection starts from
 * @param purchaseConsideration what the buyer pays today, before being negated
 *                              for the series; {@code null} when the bond has no
 *                              usable price and the purchase leg is omitted
 * @param totalCoupon           sum of all projected coupons
 * @param totalPrincipal        sum of all projected principal repayments
 * @param totalCashFlow         sum of every entry's {@code amount} — the net of
 *                              the whole series
 * @param schedule              the dated movements, earliest first
 */
public record BondCashFlowResponse(
        String isin,
        String bondName,
        LocalDate calculationDate,
        BigDecimal purchaseConsideration,
        BigDecimal totalCoupon,
        BigDecimal totalPrincipal,
        BigDecimal totalCashFlow,
        List<BondCashFlowEntry> schedule
) {
}
