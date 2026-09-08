package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic, real-pipeline integration tests covering the YTM fixes.
 *
 * Every test builds the real calculation services and computes values from
 * bond data rather than mocking the result. All tests use a fixed calculation
 * date so that outcomes are reproducible on any day.
 */
class YtmFixesIntegrationTest {

    private static final BigDecimal FACE = new BigDecimal("100");
    private static final int COUPON_SCALE = 10;

    private final CouponDateGenerator couponDateGenerator = new CouponDateGenerator();
    private final MaturityDescriptionParser parser = new MaturityDescriptionParserImpl();
    private final PrincipalRepaymentService principalService =
            new PrincipalRepaymentServiceImpl(parser);
    private final CouponCalculationService couponService = new CouponCalculationServiceImpl();
    private final CouponScheduleService couponScheduleService =
            new CouponScheduleService(couponDateGenerator);
    private final AccruedInterestService accruedService =
            new AccruedInterestServiceImpl(
                    couponScheduleService, parser, couponDateGenerator, principalService);
    private final BondCashFlowService cashFlowService =
            new BondCashFlowServiceImpl(
                    couponDateGenerator, principalService, couponService, accruedService);
    private final XirrCalculator xirrCalculator = new XirrCalculator();

    // =====================================================================
    // TEST 3: Annual amortized bond (real end-to-end)
    // =====================================================================

    @Test
    void annualAmortizedBondYieldsExpectedCouponSchedule() {
        Bond bond = new Bond();
        bond.setPrice(new BigDecimal("95.00"));
        bond.setCouponRate(new BigDecimal("10.00"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2030, 12, 31));
        bond.setIpDateDescription("31/12 Ann");
        bond.setMaturityDescription("31/12/2030 (20% each year)");

        LocalDate calcDate = LocalDate.of(2026, 1, 1);

        List<CouponPayment> coupons = coupons(bond, calcDate);
        List<PrincipalRepayment> repayments = repayments(bond, calcDate);

        assertEquals(5, coupons.size(), "Expected 5 annual coupons");
        assertEquals(5, repayments.size(), "Expected 5 annual principal repayments");

        assertCouponSequence(coupons,
                List.of("10.00", "8.00", "6.00", "4.00", "2.00"));

        // Every coupon equals outstanding principal * rate / 100 for a yearly bond.
        BigDecimal running = FACE;
        for (int i = 0; i < coupons.size(); i++) {
            BigDecimal coupon = coupons.get(i).couponAmount();
            BigDecimal expectedCoupon = running.multiply(bond.getCouponRate())
                    .divide(new BigDecimal("100"), COUPON_SCALE, RoundingMode.HALF_UP);
            assertEquals(0, coupon.compareTo(expectedCoupon),
                    "Coupon at index " + i + " must equal opening principal * rate / 100");
            running = running.subtract(repayments.get(i).principalAmount());
        }

        // Closing principal must reach zero.
        assertEquals(0, coupons.get(coupons.size() - 1).outstandingPrincipalBeforePayment()
                .subtract(repayments.get(repayments.size() - 1).principalAmount()).compareTo(BigDecimal.ZERO),
                "Closing principal after last repayment should be zero");

        BigDecimal totalPrincipal = repayments.stream()
                .map(PrincipalRepayment::principalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalPrincipal.compareTo(FACE), "Total principal repaid must be 100");

        List<XirrCalculator.CashFlow> cashFlows = cashFlowService.generateCashFlows(bond, calcDate);
        BigDecimal xirr = xirrCalculator.calculate(cashFlows);
        assertTrue(xirr.signum() > 0, "Annual amortized XIRR should be positive: " + xirr);
    }

    // =====================================================================
    // TEST 4: Monthly amortized bond (real end-to-end)
    // =====================================================================

    @Test
    void monthlyAmortizedBondYieldsExpectedCouponSequence() {
        Bond bond = new Bond();
        bond.setPrice(new BigDecimal("95.00"));
        bond.setCouponRate(new BigDecimal("12.00"));
        bond.setCouponFrequency(CouponFrequency.MONTHLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2027, 10, 1));
        bond.setIpDateDescription("1st of every month");
        bond.setMaturityDescription("01/10/2027 (10% monthly)");

        // Calculation on the 1st of a month => accrued interest is zero
        // and the amortization window starts cleanly on 2027-01-01.
        LocalDate calcDate = LocalDate.of(2026, 12, 1);

        List<CouponPayment> coupons = coupons(bond, calcDate);
        List<PrincipalRepayment> repayments = repayments(bond, calcDate);

        assertEquals(10, coupons.size(), "Expected 10 monthly coupons");
        assertEquals(10, repayments.size(), "Expected 10 monthly principal repayments");

        // coupon = outstanding principal * 12% / 12
        BigDecimal expectedCoupon = FACE.multiply(bond.getCouponRate())
                .divide(new BigDecimal("1200"), COUPON_SCALE, RoundingMode.HALF_UP);
        assertEquals(0, coupons.get(0).couponAmount().compareTo(expectedCoupon),
                "First monthly coupon should be 12% / 12 on 100 = 1.00");

        BigDecimal expectedFirst = new BigDecimal("1.00");
        assertEquals(0, coupons.get(0).couponAmount().compareTo(expectedFirst));

        for (int i = 1; i < coupons.size(); i++) {
            BigDecimal prev = coupons.get(i - 1).couponAmount();
            BigDecimal cur = coupons.get(i).couponAmount();
            BigDecimal step = new BigDecimal("0.10");
            assertEquals(0, prev.subtract(cur).compareTo(step),
                    "Monthly coupons must decline by 0.10 each month");
        }

        BigDecimal expectedLast = new BigDecimal("0.10");
        assertEquals(0, coupons.get(coupons.size() - 1).couponAmount().compareTo(expectedLast),
                "Last monthly coupon should be 0.10");

        BigDecimal totalPrincipal = repayments.stream()
                .map(PrincipalRepayment::principalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalPrincipal.compareTo(FACE), "Total principal repaid must be 100");
        assertEquals(0, repayments.get(repayments.size() - 1).remainingPrincipal()
                .compareTo(BigDecimal.ZERO), "Final outstanding principal must be zero");

        List<XirrCalculator.CashFlow> cashFlows = cashFlowService.generateCashFlows(bond, calcDate);
        BigDecimal xirr = xirrCalculator.calculate(cashFlows);
        assertTrue(xirr.signum() > 0, "Monthly amortized XIRR should be positive: " + xirr);
    }

    // =====================================================================
    // TEST 5: Quarterly amortization math
    // =====================================================================
    // CouponDateGenerator has no quarterly cadence (and is out of scope to
    // modify), so this test feeds a hand-built quarterly coupon-date list
    // directly into the real CouponCalculationService and verifies the
    // amortized coupon math: coupon = outstanding principal * rate / 4.

    @Test
    void quarterlyAmortizationUsesRateOverFour() {
        Bond bond = new Bond();
        bond.setCouponRate(new BigDecimal("12.00"));
        bond.setCouponFrequency(CouponFrequency.QUARTERLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2027, 1, 1));

        LocalDate calcDate = LocalDate.of(2026, 1, 1);

        // CouponDateGenerator has no quarterly cadence (and is out of scope to
        // modify), so we feed the real CouponCalculationService a hand-built
        // quarterly coupon-date list and an explicit 25-per-quarter repayment
        // schedule. This still exercises the genuine amortized coupon math.
        List<LocalDate> couponDates = List.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 1, 1));

        List<PrincipalRepayment> repayments = List.of(
                new PrincipalRepayment(LocalDate.of(2026, 4, 1), new BigDecimal("25"), new BigDecimal("75")),
                new PrincipalRepayment(LocalDate.of(2026, 7, 1), new BigDecimal("25"), new BigDecimal("50")),
                new PrincipalRepayment(LocalDate.of(2026, 10, 1), new BigDecimal("25"), new BigDecimal("25")),
                new PrincipalRepayment(LocalDate.of(2027, 1, 1), new BigDecimal("25"), BigDecimal.ZERO));

        List<CouponPayment> coupons = couponService.calculateCoupons(
                bond, couponDates, repayments, calcDate);

        assertEquals(4, coupons.size(), "Expected 4 quarterly coupons");

        BigDecimal running = FACE;
        for (int i = 0; i < coupons.size(); i++) {
            BigDecimal coupon = coupons.get(i).couponAmount();
            BigDecimal expectedCoupon = running.multiply(bond.getCouponRate())
                    .divide(new BigDecimal("400"), COUPON_SCALE, RoundingMode.HALF_UP);
            assertEquals(0, coupon.compareTo(expectedCoupon),
                    "Quarterly coupon must equal outstanding * rate / 4");
            running = running.subtract(repayments.get(i).principalAmount());
        }
        assertEquals(0, running.compareTo(BigDecimal.ZERO),
                "Closing principal after 4 quarterly repayments must be zero");

        // Spot-check the numeric values: 100 -> 3.00, then 75 -> 2.25.
        assertEquals(0, coupons.get(0).couponAmount().compareTo(new BigDecimal("3.00")));
        assertEquals(0, coupons.get(1).couponAmount().compareTo(new BigDecimal("2.25")));
    }

    // =====================================================================
    // TEST 6: Half-yearly amortization math
    // =====================================================================

    @Test
    void halfYearlyAmortizationUsesRateOverTwo() {
        Bond bond = new Bond();
        bond.setPrice(new BigDecimal("97.00"));
        bond.setCouponRate(new BigDecimal("8.00"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2030, 7, 1));
        bond.setIpDateDescription("01/01-01/07");
        bond.setMaturityDescription("01/07/2028 to 01/07/2030 (20% half-yearly)");

        LocalDate calcDate = LocalDate.of(2028, 6, 15);

        List<CouponPayment> coupons = coupons(bond, calcDate);
        List<PrincipalRepayment> repayments = repayments(bond, calcDate);

        assertEquals(5, coupons.size(), "Expected 5 half-yearly coupons");
        assertEquals(5, repayments.size(), "Expected 5 half-yearly repayments of 20");

        BigDecimal running = FACE;
        for (int i = 0; i < coupons.size(); i++) {
            BigDecimal coupon = coupons.get(i).couponAmount();
            BigDecimal expectedCoupon = running.multiply(bond.getCouponRate())
                    .divide(new BigDecimal("200"), COUPON_SCALE, RoundingMode.HALF_UP);
            assertEquals(0, coupon.compareTo(expectedCoupon),
                    "Half-yearly coupon must equal outstanding * rate / 2");
            running = running.subtract(repayments.get(i).principalAmount());
        }
        assertEquals(0, running.compareTo(BigDecimal.ZERO),
                "Closing principal after half-yearly repayments must be zero");

        BigDecimal totalPrincipal = repayments.stream()
                .map(PrincipalRepayment::principalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalPrincipal.compareTo(FACE), "Total principal repaid must be 100");

        List<XirrCalculator.CashFlow> cashFlows = cashFlowService.generateCashFlows(bond, calcDate);
        BigDecimal xirr = xirrCalculator.calculate(cashFlows);
        assertTrue(xirr.signum() > 0, "Half-yearly amortized XIRR should be positive: " + xirr);
    }

    // =====================================================================
    // TEST 7: Post-repayment accrued interest uses outstanding principal
    // =====================================================================

    @Test
    void accruedInterestUsesOutstandingPrincipalAfterPartialRepayment() {
        Bond bond = new Bond();
        bond.setPrice(new BigDecimal("99.00"));
        bond.setCouponRate(new BigDecimal("12.00"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2030, 7, 1));
        bond.setIpDateDescription("01/07 Ann");
        bond.setMaturityDescription("01/07/2025 to 01/07/2030 (10% each year)");

        // First 10% (10) repayment happens on 2025-07-01, i.e. before
        // settlement. A second repayment follows on 2026-07-01, after
        // settlement. Outstanding principal at settlement is therefore 90.
        LocalDate settlement = LocalDate.of(2026, 3, 15);

        BigDecimal accrued = accruedService.calculate(bond, settlement);

        CouponScheduleService.CouponSchedule schedule =
                couponScheduleService.resolve(bond, settlement);
        long daysAccrued = ChronoUnit.DAYS.between(schedule.previous(), settlement);

        BigDecimal outstandingPrincipal = new BigDecimal("90");
        BigDecimal expectedCouponAmount = outstandingPrincipal.multiply(bond.getCouponRate())
                .divide(new BigDecimal("100"), 20, RoundingMode.HALF_UP);
        BigDecimal expectedAccrued = expectedCouponAmount
                .multiply(BigDecimal.valueOf(daysAccrued))
                .divide(new BigDecimal("365"), 20, RoundingMode.HALF_UP);

        System.out.println();
        System.out.println("POST-REPAYMENT ACCRUED INTEREST");
        System.out.println("Face Value           : " + FACE);
        System.out.println("Outstanding Principal: " + outstandingPrincipal);
        System.out.println("Coupon Amount (full) : " + expectedCouponAmount);
        System.out.println("Days Accrued         : " + daysAccrued);
        System.out.println("Accrued Interest     : " + accrued);

        assertEquals(0, accrued.compareTo(expectedAccrued),
                "Accrued interest must be based on outstanding principal 90, not face value 100");

        // Sanity: if it had (wrongly) used 100 the accrued interest would differ.
        BigDecimal faceBasedAccrued = FACE.multiply(bond.getCouponRate())
                .divide(new BigDecimal("100"), 20, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysAccrued))
                .divide(new BigDecimal("365"), 20, RoundingMode.HALF_UP);
        assertTrue(accrued.compareTo(faceBasedAccrued) != 0,
                "Accrued interest must differ from a face-value-based accrual");
    }

    // =====================================================================
    // TEST 9: Staged IP amortization is preserved
    // =====================================================================

    @Test
    void stagedIpAmortizationFullyRepaysPrincipal() {
        Bond bond = new Bond();
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");

        LocalDate calcDate = LocalDate.of(2026, 9, 3);

        List<CouponPayment> coupons = coupons(bond, calcDate);
        List<PrincipalRepayment> repayments = repayments(bond, calcDate);

        BigDecimal totalPrincipal = repayments.stream()
                .map(PrincipalRepayment::principalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalPrincipal.compareTo(FACE),
                "Staged IP amortization must repay the full 100 principal");

        assertEquals(0, repayments.get(repayments.size() - 1).remainingPrincipal()
                .compareTo(BigDecimal.ZERO),
                "Final outstanding principal must be zero");

        assertTrue(coupons.size() > 0, "Staged bond must still pay coupons");
        assertTrue(coupons.stream().allMatch(c -> c.couponAmount().signum() >= 0),
                "Coupons must be non-negative");

        List<XirrCalculator.CashFlow> cashFlows = cashFlowService.generateCashFlows(bond, calcDate);
        BigDecimal xirr = xirrCalculator.calculate(cashFlows);
        assertTrue(xirr.signum() > 0, "Staged IP XIRR should be positive: " + xirr);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<CouponPayment> coupons(Bond bond, LocalDate calcDate) {
        return couponService.calculateCoupons(
                bond, couponDateGenerator.generate(bond, calcDate),
                principalService.generateRepayments(bond,
                        couponDateGenerator.generate(bond, calcDate), calcDate),
                calcDate);
    }

    private List<PrincipalRepayment> repayments(Bond bond, LocalDate calcDate) {
        return principalService.generateRepayments(bond,
                couponDateGenerator.generate(bond, calcDate), calcDate);
    }

    private void assertCouponSequence(
            List<CouponPayment> coupons, List<String> expected) {
        assertEquals(expected.size(), coupons.size(), "Coupon count mismatch");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(0, coupons.get(i).couponAmount()
                    .compareTo(new BigDecimal(expected.get(i))),
                    "Coupon at index " + i);
        }
    }
}
