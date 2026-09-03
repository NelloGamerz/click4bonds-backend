package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

class CouponCalculationServiceImplTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);
    private final CouponCalculationServiceImpl service = new CouponCalculationServiceImpl();

    @Test
    void shouldCalculateHalfYearlyCouponWithoutAmortization() {
        Bond bond = bond("7.20", CouponFrequency.HALF_YEARLY);
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 9, 26), LocalDate.of(2027, 3, 26), LocalDate.of(2027, 9, 26));

        List<CouponPayment> result = service.calculateCoupons(
                bond, dates, repayments(List.of(LocalDate.of(2031, 9, 26)), List.of("100")), CALCULATION_DATE);

        assertAmounts(result, List.of("3.60", "3.60", "3.60"));
    }

    @Test
    void shouldCalculateStagedAmortizationUsingOutstandingPrincipalBeforeEachPayment() {
        Bond bond = bond("7.20", CouponFrequency.HALF_YEARLY);
        List<LocalDate> dates = stagedDates();
        List<CouponPayment> result = service.calculateCoupons(
                bond, dates, repayments(dates, List.of("2.5", "2.5", "2.5", "10", "10", "10", "10", "10", "10", "10", "22.5")),
                CALCULATION_DATE);

        printCouponTable(result, bond);
        assertAmounts(result, List.of("3.60", "3.51", "3.42", "3.33", "2.97", "2.61",
                "2.25", "1.89", "1.53", "1.17", "0.81"));
        assertEquals(List.of("100", "97.5", "95", "92.5", "82.5", "72.5", "62.5", "52.5", "42.5", "32.5", "22.5"),
                result.stream().map(payment -> payment.outstandingPrincipalBeforePayment().stripTrailingZeros().toPlainString()).toList());
    }

    @Test
    void shouldCalculateCouponBeforeSameDayRepayment() {
        Bond bond = bond("10", CouponFrequency.HALF_YEARLY);
        LocalDate paymentDate = LocalDate.of(2027, 3, 26);

        CouponPayment result = service.calculateCoupons(
                bond, List.of(paymentDate),
                List.of(new PrincipalRepayment(paymentDate, new BigDecimal("20"), new BigDecimal("80"))),
                CALCULATION_DATE).get(0);

        assertBigDecimalEquals("100", result.outstandingPrincipalBeforePayment());
        assertBigDecimalEquals("5", result.couponAmount());
    }

    @Test
    void shouldSupportMonthlyFrequency() {
        Bond bond = bond("12", CouponFrequency.MONTHLY);
        List<CouponPayment> result = service.calculateCoupons(bond,
                List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), List.of(), CALCULATION_DATE);

        assertAmounts(result, List.of("1", "1"));
    }

    @Test
    void shouldSupportQuarterlyFrequency() {
        assertAmounts(service.calculateCoupons(bond("8", CouponFrequency.QUARTERLY),
                List.of(LocalDate.of(2026, 10, 1)), List.of(), CALCULATION_DATE), List.of("2"));
    }

    @Test
    void shouldSupportYearlyFrequency() {
        assertAmounts(service.calculateCoupons(bond("8", CouponFrequency.YEARLY),
                List.of(LocalDate.of(2027, 9, 26)), List.of(), CALCULATION_DATE), List.of("8"));
    }

    @Test
    void shouldCarryOutstandingPrincipalAcrossPayments() {
        Bond bond = bond("12", CouponFrequency.HALF_YEARLY);
        List<LocalDate> dates = List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 10, 1));
        List<CouponPayment> result = service.calculateCoupons(bond, dates,
                repayments(dates, List.of("25", "25", "50")), CALCULATION_DATE);

        assertAmounts(result, List.of("6", "4.5", "3"));
    }

    @Test
    void shouldFilterSortAndDeduplicateCouponDates() {
        Bond bond = bond("8", CouponFrequency.YEARLY);
        List<LocalDate> dates = List.of(LocalDate.of(2027, 9, 26), LocalDate.of(2026, 9, 26),
                LocalDate.of(2027, 9, 26), LocalDate.of(2028, 3, 26));

        List<CouponPayment> result = service.calculateCoupons(bond, dates, List.of(), CALCULATION_DATE);

        assertEquals(List.of(LocalDate.of(2026, 9, 26), LocalDate.of(2027, 9, 26), LocalDate.of(2028, 3, 26)),
                result.stream().map(payment -> payment.date()).toList());
    }

    @Test
    void shouldExcludeCouponDatesOnOrBeforeCalculationDate() {
        Bond bond = bond("8", CouponFrequency.YEARLY);
        List<CouponPayment> result = service.calculateCoupons(bond,
                List.of(LocalDate.of(2026, 9, 26), LocalDate.of(2027, 3, 26), LocalDate.of(2027, 9, 26), LocalDate.of(2028, 3, 26)),
                List.of(), LocalDate.of(2027, 4, 1));

        assertEquals(List.of(LocalDate.of(2027, 9, 26), LocalDate.of(2028, 3, 26)),
                result.stream().map(payment -> payment.date()).toList());
    }

    @Test
    void shouldAllowZeroCouponRate() {
        List<CouponPayment> result = service.calculateCoupons(bond("0", CouponFrequency.YEARLY),
                List.of(LocalDate.of(2027, 9, 26)), List.of(), CALCULATION_DATE);

        assertBigDecimalEquals("0", result.get(0).couponAmount());
    }

    @Test
    void shouldCalculateMaturityCouponBeforeMaturityRepayment() {
        Bond bond = bond("7.20", CouponFrequency.HALF_YEARLY);
        LocalDate maturity = LocalDate.of(2031, 9, 26);
        List<CouponPayment> result = service.calculateCoupons(bond, List.of(maturity),
                List.of(new PrincipalRepayment(maturity, new BigDecimal("22.5"), BigDecimal.ZERO)), CALCULATION_DATE);

        assertBigDecimalEquals("22.5", result.get(0).outstandingPrincipalBeforePayment());
        assertBigDecimalEquals("0.81", result.get(0).couponAmount());
    }

    @Test
    void shouldNotInventCouponAtMaturityWhenDateIsNotSupplied() {
        Bond bond = bond("7.20", CouponFrequency.HALF_YEARLY);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));

        List<CouponPayment> result = service.calculateCoupons(bond,
                List.of(LocalDate.of(2031, 3, 26)),
                List.of(new PrincipalRepayment(bond.getMaturityDate(), new BigDecimal("100"), BigDecimal.ZERO)),
                CALCULATION_DATE);

        assertEquals(List.of(LocalDate.of(2031, 3, 26)), result.stream().map(payment -> payment.date()).toList());
    }

    @Test
    void shouldRejectNullAndInvalidInputs() {
        Bond bond = bond("7.20", CouponFrequency.HALF_YEARLY);
        assertMessage("Bond cannot be null", () -> service.calculateCoupons(null, List.of(), List.of(), CALCULATION_DATE));
        assertMessage("Coupon dates cannot be null", () -> service.calculateCoupons(bond, null, List.of(), CALCULATION_DATE));
        assertMessage("Principal repayments cannot be null", () -> service.calculateCoupons(bond, List.of(), null, CALCULATION_DATE));
        assertMessage("Calculation date cannot be null", () -> service.calculateCoupons(bond, List.of(), List.of(), null));

        bond.setCouponRate(null);
        assertMessage("Coupon rate is required", () -> service.calculateCoupons(bond, List.of(), List.of(), CALCULATION_DATE));
        bond.setCouponRate(new BigDecimal("-1"));
        assertMessage("Coupon rate cannot be negative", () -> service.calculateCoupons(bond, List.of(), List.of(), CALCULATION_DATE));
        bond.setCouponRate(BigDecimal.ONE);
        bond.setCouponFrequency(null);
        assertMessage("Coupon frequency is required", () -> service.calculateCoupons(bond, List.of(), List.of(), CALCULATION_DATE));
        bond.setCouponFrequency(CouponFrequency.AT_MATURITY);
        assertMessage("Unsupported coupon frequency: AT_MATURITY",
                () -> service.calculateCoupons(bond, List.of(), List.of(), CALCULATION_DATE));
    }

    @Test
    void shouldIntegrateWithRealPrincipalRepaymentAndParserServices() {
        Bond bond = bond("7.20", CouponFrequency.HALF_YEARLY);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");

        List<LocalDate> dates = stagedDates();
        PrincipalRepaymentService principalService = new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl());
        List<PrincipalRepayment> repayments = principalService.generateRepayments(bond, dates, CALCULATION_DATE);
        List<CouponPayment> coupons = service.calculateCoupons(bond, dates, repayments, CALCULATION_DATE);

        assertAmounts(coupons, List.of("3.60", "3.51", "3.42", "3.33", "2.97", "2.61",
                "2.25", "1.89", "1.53", "1.17", "0.81"));
    }

    private Bond bond(String couponRate, CouponFrequency frequency) {
        Bond bond = new Bond();
        bond.setCouponRate(new BigDecimal(couponRate));
        bond.setCouponFrequency(frequency);
        return bond;
    }

    private List<LocalDate> stagedDates() {
        return List.of(LocalDate.of(2026, 9, 26), LocalDate.of(2027, 3, 26), LocalDate.of(2027, 9, 26),
                LocalDate.of(2028, 3, 26), LocalDate.of(2028, 9, 26), LocalDate.of(2029, 3, 26),
                LocalDate.of(2029, 9, 26), LocalDate.of(2030, 3, 26), LocalDate.of(2030, 9, 26),
                LocalDate.of(2031, 3, 26), LocalDate.of(2031, 9, 26));
    }

    private List<PrincipalRepayment> repayments(List<LocalDate> dates, List<String> amounts) {
        List<PrincipalRepayment> repayments = new ArrayList<>();
        BigDecimal remaining = BigDecimal.valueOf(100);
        for (int index = 0; index < dates.size(); index++) {
            BigDecimal amount = new BigDecimal(amounts.get(index));
            remaining = remaining.subtract(amount);
            repayments.add(new PrincipalRepayment(dates.get(index), amount, remaining));
        }
        return repayments;
    }

    private void assertAmounts(List<CouponPayment> payments, List<String> expected) {
        assertEquals(expected.size(), payments.size());
        for (int index = 0; index < expected.size(); index++) {
            assertBigDecimalEquals(expected.get(index), payments.get(index).couponAmount());
        }
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + " but was " + actual);
    }

    private void assertMessage(String expected, Executable action) {
        assertEquals(expected, assertThrows(IllegalArgumentException.class, action).getMessage());
    }

    private void printCouponTable(List<CouponPayment> payments, Bond bond) {
        System.out.println("============================================================");
        System.out.println("COUPON CALCULATION TEST");
        System.out.println("============================================================");
        System.out.println("Calculation Date : " + CALCULATION_DATE);
        System.out.println("Face Value       : 100");
        System.out.println("Coupon Rate      : " + bond.getCouponRate() + "%");
        System.out.println("Frequency        : " + bond.getCouponFrequency());
        System.out.println("------------------------------------------------------------");
        System.out.println("Date          Outstanding Before    Coupon");
        System.out.println("------------------------------------------------------------");
        payments.forEach(payment -> System.out.printf("%-13s %-21s %-10s%n", payment.date(),
                payment.outstandingPrincipalBeforePayment(), payment.couponAmount()));
        System.out.println("------------------------------------------------------------");
        System.out.println("============================================================");
    }
}