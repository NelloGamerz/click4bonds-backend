package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

class PrincipalRepaymentServiceImplTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    private Bond bond;
    private PrincipalRepaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        bond = new Bond();
        service = new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl());
    }

    @Test
    void shouldRepayFullPrincipalAtMaturityEvenWhenMaturityIsNotCouponDate() {
        setBond(LocalDate.of(2031, 9, 26), "26-09-2031");

        List<PrincipalRepayment> result = service.generateRepayments(
                bond, List.of(LocalDate.of(2031, 9, 25)), CALCULATION_DATE);

        assertRepayment(result.get(0), LocalDate.of(2031, 9, 26), "100", "0");
        assertEquals(1, result.size());
    }

    @Test
    void shouldReturnNoRepaymentForPerpetualBond() {
                setBond(null, "Perp");

        assertTrue(service.generateRepayments(bond, List.of(), CALCULATION_DATE).isEmpty());
    }

    @Test
    void shouldGenerateStagedIpAmortizationAndSettleRemainingPrincipal() {
        setBond(LocalDate.of(2031, 9, 26),
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
        List<LocalDate> couponDates = List.of(
                LocalDate.of(2031, 9, 26), LocalDate.of(2026, 9, 26), LocalDate.of(2029, 3, 26),
                LocalDate.of(2027, 9, 26), LocalDate.of(2030, 9, 26), LocalDate.of(2028, 3, 26),
                LocalDate.of(2027, 3, 26), LocalDate.of(2031, 3, 26), LocalDate.of(2028, 9, 26),
                LocalDate.of(2029, 9, 26), LocalDate.of(2030, 3, 26));

        List<PrincipalRepayment> result = service.generateRepayments(bond, couponDates, CALCULATION_DATE);

        String[][] expected = {
                {"2026-09-26", "2.50", "97.50"}, {"2027-03-26", "2.50", "95.00"},
                {"2027-09-26", "2.50", "92.50"}, {"2028-03-26", "10.00", "82.50"},
                {"2028-09-26", "10.00", "72.50"}, {"2029-03-26", "10.00", "62.50"},
                {"2029-09-26", "10.00", "52.50"}, {"2030-03-26", "10.00", "42.50"},
                {"2030-09-26", "10.00", "32.50"}, {"2031-03-26", "10.00", "22.50"},
                {"2031-09-26", "22.50", "0.00"}};

        printRepaymentTable(result);
        assertEquals(expected.length, result.size());
        for (int index = 0; index < expected.length; index++) {
            assertRepayment(result.get(index), LocalDate.parse(expected[index][0]), expected[index][1], expected[index][2]);
        }
        assertBigDecimalEquals("100.00", result.stream()
                .map(PrincipalRepayment::principalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void shouldExcludeRepaymentsOnOrBeforeCalculationDate() {
        setBond(LocalDate.of(2031, 9, 26),
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
        List<PrincipalRepayment> result = service.generateRepayments(bond, List.of(
                LocalDate.of(2026, 9, 26), LocalDate.of(2027, 3, 26),
                LocalDate.of(2027, 9, 26), LocalDate.of(2031, 9, 26)), LocalDate.of(2027, 4, 1));

        assertEquals(List.of(LocalDate.of(2027, 9, 26), LocalDate.of(2031, 9, 26)),
                result.stream().map(PrincipalRepayment::date).toList());
        assertBigDecimalEquals("92.5", result.get(0).remainingPrincipal());
    }

    @Test
    void shouldSettleRemainingPrincipalWhenMaturityIsAbsentFromCouponDates() {
        setBond(LocalDate.of(2031, 9, 26), "26-09-2031 (60% on Each IP till 2030)");
        List<PrincipalRepayment> result = service.generateRepayments(bond, List.of(
                LocalDate.of(2027, 9, 26)), CALCULATION_DATE);

        assertEquals(LocalDate.of(2031, 9, 26), result.get(result.size() - 1).date());
        assertBigDecimalEquals("40", result.get(result.size() - 1).principalAmount());
        assertBigDecimalEquals("0", result.get(result.size() - 1).remainingPrincipal());
    }

    @Test
    void shouldGenerateFixedFrequencyRepaymentsFromRuleDates() {
        LocalDate start = LocalDate.of(2027, 1, 15);
        LocalDate maturity = LocalDate.of(2028, 1, 15);
        service = new PrincipalRepaymentServiceImpl((description, normalizedDate) -> new MaturitySchedule(
                maturity, List.of(new FixedFrequencyAmortizationRule(
                        BigDecimal.TEN, start, maturity, CouponFrequency.QUARTERLY)), false));
        setBond(maturity, "controlled schedule");

        List<PrincipalRepayment> result = service.generateRepayments(bond, List.of(), CALCULATION_DATE);

        assertEquals(List.of(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 4, 15),
                LocalDate.of(2027, 7, 15), LocalDate.of(2027, 10, 15), maturity),
                result.stream().map(PrincipalRepayment::date).toList());
        assertBigDecimalEquals("60", result.get(result.size() - 1).principalAmount());
    }

    @Test
    void shouldCapScheduledRepaymentAtRemainingPrincipal() {
        LocalDate maturity = LocalDate.of(2028, 1, 1);
        service = new PrincipalRepaymentServiceImpl((description, normalizedDate) -> new MaturitySchedule(
                maturity, List.of(new FixedFrequencyAmortizationRule(
                        BigDecimal.valueOf(60), LocalDate.of(2027, 1, 1), maturity, CouponFrequency.YEARLY)), false));
        setBond(maturity, "controlled schedule");

        List<PrincipalRepayment> result = service.generateRepayments(bond, List.of(), CALCULATION_DATE);

        assertBigDecimalEquals("60", result.get(0).principalAmount());
        assertBigDecimalEquals("40", result.get(1).principalAmount());
        assertBigDecimalEquals("0", result.get(1).remainingPrincipal());
    }

    @Test
    void shouldRejectOverlappingRules() {
        LocalDate date = LocalDate.of(2027, 1, 1);
        service = new PrincipalRepaymentServiceImpl((description, normalizedDate) -> new MaturitySchedule(
                LocalDate.of(2028, 1, 1), List.of(
                        new FixedFrequencyAmortizationRule(BigDecimal.TEN, date, date, CouponFrequency.YEARLY),
                        new FixedFrequencyAmortizationRule(BigDecimal.ONE, date, date, CouponFrequency.YEARLY)), false));
        setBond(LocalDate.of(2028, 1, 1), "controlled schedule");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.generateRepayments(bond, List.of(), CALCULATION_DATE));
        assertTrue(exception.getMessage().contains("Multiple amortization rules apply on 2027-01-01"));
    }

    @Test
    void shouldSortUnorderedCouponDatesAndExcludePastDates() {
        setBond(LocalDate.of(2030, 1, 1), "01-01-2030 (10% on Each IP till 2029)");
        List<PrincipalRepayment> result = service.generateRepayments(bond, List.of(
                LocalDate.of(2028, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), CALCULATION_DATE);

        assertEquals(List.of(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1), LocalDate.of(2030, 1, 1)),
                result.stream().map(PrincipalRepayment::date).toList());
    }

    @Test
    void shouldRejectNullInputs() {
        assertEquals("Bond cannot be null", assertThrows(IllegalArgumentException.class,
                () -> service.generateRepayments(null, List.of(), CALCULATION_DATE)).getMessage());
        bond.setMaturityDate(LocalDate.of(2030, 1, 1));
        assertEquals("Coupon dates cannot be null", assertThrows(IllegalArgumentException.class,
                () -> service.generateRepayments(bond, null, CALCULATION_DATE)).getMessage());
        assertEquals("Calculation date cannot be null", assertThrows(IllegalArgumentException.class,
                () -> service.generateRepayments(bond, List.of(), null)).getMessage());
    }

    private void setBond(LocalDate maturityDate, String description) {
        bond.setMaturityDate(maturityDate);
        bond.setMaturityDescription(description);
    }

    private void assertRepayment(PrincipalRepayment actual, LocalDate date, String principal, String remaining) {
        assertEquals(date, actual.date());
        assertBigDecimalEquals(principal, actual.principalAmount());
        assertBigDecimalEquals(remaining, actual.remainingPrincipal());
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertBigDecimalEquals(new BigDecimal(expected), actual);
    }

    private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), "Expected " + expected + " but was " + actual);
    }

    private void printRepaymentTable(List<PrincipalRepayment> repayments) {
        System.out.println("============================================================");
        System.out.println("PRINCIPAL REPAYMENT TEST");
        System.out.println("============================================================");
        System.out.println("Calculation Date : " + CALCULATION_DATE);
        System.out.println("Face Value       : 100");
        System.out.println("------------------------------------------------------------");
        System.out.println("Date          Principal       Remaining");
        System.out.println("------------------------------------------------------------");
        repayments.forEach(repayment -> System.out.printf("%-13s %-15s %-15s%n",
                repayment.date(), repayment.principalAmount(), repayment.remainingPrincipal()));
        System.out.println("------------------------------------------------------------");
        System.out.println("Total Principal: " + repayments.stream()
                .map(PrincipalRepayment::principalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        System.out.println("============================================================");
    }
}