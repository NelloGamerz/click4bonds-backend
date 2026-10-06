package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowEntry;
import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Enums.BondCashFlowType;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Covers the schedule view of the projection exposed by
 * {@code GET /api/bonds/{isin}/cashflow}.
 *
 * <p>
 * The schedule and the XIRR series come from the same projection, so the most
 * valuable test here is the reconciliation between them: if they ever disagree,
 * the bond's published YTM no longer matches the cash flows shown to the user.
 */
class BondCashFlowScheduleTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    @Test
    void shouldLeadScheduleWithPurchaseLegAndReportTotals() {

        Bond bond = pricedBond();

        BondCashFlowResponse response = service().generateSchedule(bond, CALCULATION_DATE);

        BondCashFlowEntry purchase = response.schedule().get(0);

        assertEquals(BondCashFlowType.PURCHASE, purchase.type());
        assertEquals(CALCULATION_DATE, purchase.date());
        assertNull(purchase.couponAmount(), "the purchase leg carries no coupon");
        assertNull(purchase.principalAmount(), "the purchase leg carries no principal");
        assertTrue(
                purchase.amount().signum() < 0,
                "the purchase leg is an outflow, so its amount must be negative");

        assertEquals(purchase.amount().negate(), response.purchaseConsideration());

        /*
         * The schedule is what the holder receives, plus that one outflow, so
         * the totals must reconcile with the entries themselves.
         */
        assertEquals(total(response, BondCashFlowEntry::couponAmount), response.totalCoupon());
        assertEquals(total(response, BondCashFlowEntry::principalAmount), response.totalPrincipal());
        assertEquals(total(response, BondCashFlowEntry::amount), response.totalCashFlow());

        assertEquals(BondCashFlowType.COUPON, response.schedule().get(1).type());
    }

    @Test
    void shouldKeepCouponsAndPrincipalOnTheirOwnComponentsOfASharedDate() {

        Bond bond = pricedBond();

        List<BondCashFlowEntry> entries = service()
                .generateSchedule(bond, CALCULATION_DATE)
                .schedule();

        for (BondCashFlowEntry entry : entries) {
            if (entry.type() == BondCashFlowType.COUPON_AND_PRINCIPAL) {
                assertEquals(
                        entry.couponAmount().add(entry.principalAmount()),
                        entry.amount(),
                        "an amortizing coupon date must total its two components");
            }
        }

        assertTrue(
                entries.stream().noneMatch(entry -> entry.amount().signum() == 0),
                "no projected date should carry a zero amount");
    }

    @Test
    void shouldStillProjectCouponsWhenBondHasNoUsablePrice() {

        Bond bond = pricedBond();
        bond.setPrice(null);

        BondCashFlowResponse response = service().generateSchedule(bond, CALCULATION_DATE);

        assertNull(
                response.purchaseConsideration(),
                "without a price there is nothing to pay, so the leg is omitted");

        assertTrue(
                response.schedule().stream()
                        .noneMatch(entry -> entry.type() == BondCashFlowType.PURCHASE),
                "an unpriced bond has no purchase entry");

        assertTrue(
                response.totalCoupon().signum() > 0,
                "the coupons the holder receives do not depend on the price");

        assertTrue(
                response.schedule().stream().allMatch(entry -> entry.couponAmount() != null),
                "every remaining entry is a coupon");
    }

    @Test
    void shouldReconcileScheduleWithTheXirrSeries() {

        Bond bond = pricedBond();
        BondCashFlowServiceImpl cashFlowService = service();

        List<XirrCalculator.CashFlow> xirrSeries = cashFlowService.generateCashFlows(bond, CALCULATION_DATE);

        Map<LocalDate, BigDecimal> fromSchedule = new TreeMap<>();
        for (BondCashFlowEntry entry : cashFlowService.generateSchedule(bond, CALCULATION_DATE).schedule()) {
            fromSchedule.merge(entry.date(), entry.amount(), BigDecimal::add);
        }

        List<Map.Entry<LocalDate, BigDecimal>> expected = xirrSeries.stream()
                .map(flow -> Map.entry(flow.date(), flow.amount()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();

        assertEquals(
                expected,
                List.copyOf(fromSchedule.entrySet()),
                "the published schedule must net to exactly the series the YTM is discounted from");
    }

    private static BigDecimal total(
            BondCashFlowResponse response,
            java.util.function.Function<BondCashFlowEntry, BigDecimal> component) {

        return response.schedule().stream()
                .map(component)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BondCashFlowServiceImpl service() {
        return new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));
    }

    private static Bond pricedBond() {
        Bond bond = new Bond();
        bond.setIsin("INE123A07012");
        bond.setName("Test Bond");
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setPrice(new BigDecimal("97.65"));
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setMaturityType(MaturityType.FIXED);
        bond.setIpDateDescription("26/03-26/09");
        return bond;
    }
}
