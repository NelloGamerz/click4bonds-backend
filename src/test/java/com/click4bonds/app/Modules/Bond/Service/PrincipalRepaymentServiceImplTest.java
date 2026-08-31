package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

class PrincipalRepaymentServiceImplTest {

    private PrincipalRepaymentServiceImpl service;

    private Bond bond;

    @BeforeEach
    void setUp() {

        service = new PrincipalRepaymentServiceImpl();

        bond = new Bond();
    }

    // ============================================================
    // NORMAL MATURITY
    // ============================================================

    @Test
    void shouldGenerateFullPrincipalAtMaturity() {

        LocalDate maturityDate = LocalDate.of(2033, 9, 11);

        bond.setMaturityDate(maturityDate);
        bond.setMaturityDescription("26-09-2033");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2032, 9, 11),
                LocalDate.of(2033, 9, 11));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldGenerateFullPrincipalAtMaturity",
                result);

        assertEquals(1, result.size());

        PrincipalRepayment repayment = result.get(0);

        assertEquals(
                maturityDate,
                repayment.date());

        assertBigDecimalEquals(
                BigDecimal.valueOf(100),
                repayment.principalAmount());

        assertBigDecimalEquals(
                BigDecimal.ZERO,
                repayment.remainingPrincipal());
    }

    @Test
    void shouldNotGenerateMaturityRepaymentWhenMaturityIsNotCouponDate() {

        LocalDate maturityDate = LocalDate.of(2033, 9, 11);

        bond.setMaturityDate(maturityDate);
        bond.setMaturityDescription("26-09-2033");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2032, 9, 11),
                LocalDate.of(2033, 9, 10));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldNotGenerateMaturityRepaymentWhenMaturityIsNotCouponDate",
                result);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGenerateNormalMaturityWhenDescriptionIsBlank() {

        LocalDate maturityDate = LocalDate.of(2033, 9, 11);

        bond.setMaturityDate(maturityDate);
        bond.setMaturityDescription("   ");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2033, 9, 11));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldGenerateNormalMaturityWhenDescriptionIsBlank",
                result);

        assertEquals(1, result.size());

        assertEquals(
                maturityDate,
                result.get(0).date());

        assertBigDecimalEquals(
                BigDecimal.valueOf(100),
                result.get(0).principalAmount());
    }

    // ============================================================
    // SIMPLE AMORTIZATION
    // ============================================================

    @Test
    void shouldGenerate10PercentAmortizationEachYear() {

        bond.setMaturityDate(
                LocalDate.of(2033, 9, 11));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2033 (10% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2027, 11, 9),
                LocalDate.of(2028, 11, 9),
                LocalDate.of(2029, 11, 9),
                LocalDate.of(2030, 11, 9),
                LocalDate.of(2031, 11, 9),
                LocalDate.of(2032, 11, 9),
                LocalDate.of(2033, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldGenerate10PercentAmortizationEachYear",
                result);

        assertEquals(10, result.size());

        for (int i = 0; i < result.size(); i++) {

            PrincipalRepayment repayment = result.get(i);

            assertBigDecimalEquals(
                    BigDecimal.TEN,
                    repayment.principalAmount());

            BigDecimal expectedRemaining = BigDecimal.valueOf(
                    90 - (i * 10));

            assertBigDecimalEquals(
                    expectedRemaining,
                    repayment.remainingPrincipal());
        }
    }

    @Test
    void shouldIgnoreCouponDatesBeforeAmortizationStart() {

        bond.setMaturityDate(
                LocalDate.of(2033, 9, 11));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2033 (10% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2023, 11, 9),
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldIgnoreCouponDatesBeforeAmortizationStart",
                result);

        assertEquals(2, result.size());

        assertEquals(
                LocalDate.of(2024, 11, 9),
                result.get(0).date());

        assertEquals(
                LocalDate.of(2025, 11, 9),
                result.get(1).date());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(0).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(1).principalAmount());
    }

    @Test
    void shouldIgnoreCouponDatesAfterAmortizationEnd() {

        bond.setMaturityDate(
                LocalDate.of(2033, 9, 11));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2026 (10% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2027, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldIgnoreCouponDatesAfterAmortizationEnd",
                result);

        assertEquals(3, result.size());

        assertEquals(
                LocalDate.of(2026, 11, 9),
                result.get(2).date());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(0).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(1).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(2).principalAmount());
    }

    // ============================================================
    // 100% REPAYMENT
    // ============================================================

    @Test
    void shouldStopWhenPrincipalIsFullyRepaid() {

        bond.setMaturityDate(
                LocalDate.of(2026, 9, 11));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2026 (50% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9),
                LocalDate.of(2026, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldStopWhenPrincipalIsFullyRepaid",
                result);

        assertEquals(2, result.size());

        assertBigDecimalEquals(
                BigDecimal.valueOf(50),
                result.get(0).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.valueOf(50),
                result.get(0).remainingPrincipal());

        assertBigDecimalEquals(
                BigDecimal.valueOf(50),
                result.get(1).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.ZERO,
                result.get(1).remainingPrincipal());
    }

    // ============================================================
    // PARTIAL AMORTIZATION + MATURITY
    // ============================================================

    @Test
    void shouldAddRemainingPrincipalAtMaturity() {

        LocalDate maturityDate = LocalDate.of(2033, 11, 9);

        bond.setMaturityDate(maturityDate);

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2033 (20% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2027, 11, 9),
                LocalDate.of(2028, 11, 9),
                LocalDate.of(2029, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldAddRemainingPrincipalAtMaturity",
                result);

        /*
         * Five payments of 20% repay
         * the entire ₹100 principal.
         */
        assertEquals(5, result.size());

        assertBigDecimalEquals(
                BigDecimal.valueOf(20),
                result.get(0).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.valueOf(20),
                result.get(1).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.valueOf(20),
                result.get(2).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.valueOf(20),
                result.get(3).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.valueOf(20),
                result.get(4).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.ZERO,
                result.get(4).remainingPrincipal());
    }

    @Test
    void shouldAddRemainingPrincipalToExistingMaturityRepayment() {

        LocalDate maturityDate = LocalDate.of(2028, 11, 9);

        bond.setMaturityDate(maturityDate);

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2028 (10% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2027, 11, 9),
                LocalDate.of(2028, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldAddRemainingPrincipalToExistingMaturityRepayment",
                result);

        assertEquals(5, result.size());

        /*
         * Each scheduled payment is 10.
         *
         * After five payments, 50 remains.
         *
         * The final maturity payment becomes:
         *
         * 10 + 50 = 60
         */
        PrincipalRepayment maturityRepayment = result.get(result.size() - 1);

        assertEquals(
                maturityDate,
                maturityRepayment.date());

        assertBigDecimalEquals(
                BigDecimal.valueOf(60),
                maturityRepayment.principalAmount());

        assertBigDecimalEquals(
                BigDecimal.ZERO,
                maturityRepayment.remainingPrincipal());
    }

    // ============================================================
    // DATE ORDERING
    // ============================================================

    @Test
    void shouldReturnRepaymentsSortedByDate() {

        bond.setMaturityDate(
                LocalDate.of(2028, 11, 9));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2028 (10% each year)");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2027, 11, 9),
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2025, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.of(2026, 8, 26));

        printRepayments(
                "shouldReturnRepaymentsSortedByDate",
                result);

        assertEquals(4, result.size());

        assertEquals(
                LocalDate.of(2024, 11, 9),
                result.get(0).date());

        assertEquals(
                LocalDate.of(2025, 11, 9),
                result.get(1).date());

        assertEquals(
                LocalDate.of(2026, 11, 9),
                result.get(2).date());

        assertEquals(
                LocalDate.of(2027, 11, 9),
                result.get(3).date());
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    @Test
    void shouldRejectNullBond() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        null,
                        List.of(),
                        LocalDate.now()));

        System.out.println();
        System.out.println(
                "shouldRejectNullBond -> "
                        + exception.getMessage());

        assertEquals(
                "Bond cannot be null",
                exception.getMessage());
    }

    @Test
    void shouldRejectNullCouponDates() {

        bond.setMaturityDate(
                LocalDate.of(2033, 9, 11));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        bond,
                        null,
                        LocalDate.now()));

        System.out.println();
        System.out.println(
                "shouldRejectNullCouponDates -> "
                        + exception.getMessage());

        assertEquals(
                "Coupon dates cannot be null",
                exception.getMessage());
    }

    @Test
    void shouldRejectNullCalculationDate() {

        bond.setMaturityDate(
                LocalDate.of(2033, 9, 11));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        bond,
                        List.of(),
                        null));

        System.out.println();
        System.out.println(
                "shouldRejectNullCalculationDate -> "
                        + exception.getMessage());

        assertEquals(
                "Calculation date cannot be null",
                exception.getMessage());
    }

    @Test
    void shouldRejectNullMaturityDate() {

        bond.setMaturityDate(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        bond,
                        List.of(),
                        LocalDate.now()));

        System.out.println();
        System.out.println(
                "shouldRejectNullMaturityDate -> "
                        + exception.getMessage());

        assertEquals(
                "Maturity date is required",
                exception.getMessage());
    }

    // ============================================================
    // INVALID PERCENTAGE
    // ============================================================

    @Test
    void shouldRejectZeroPercentage() {

        bond.setMaturityDate(
                LocalDate.of(2030, 11, 9));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2030 (0% each year)");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        bond,
                        List.of(
                                LocalDate.of(2025, 11, 9)),
                        LocalDate.now()));

        System.out.println();
        System.out.println(
                "shouldRejectZeroPercentage -> "
                        + exception.getMessage());

        assertEquals(
                "Amortization percentage must be positive",
                exception.getMessage());
    }

    @Test
    void shouldRejectPercentageGreaterThan100() {

        bond.setMaturityDate(
                LocalDate.of(2030, 11, 9));

        bond.setMaturityDescription(
                "9/11/2024 to 9/11/2030 (101% each year)");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        bond,
                        List.of(
                                LocalDate.of(2025, 11, 9)),
                        LocalDate.now()));

        System.out.println();
        System.out.println(
                "shouldRejectPercentageGreaterThan100 -> "
                        + exception.getMessage());

        assertEquals(
                "Amortization percentage cannot exceed 100",
                exception.getMessage());
    }

    // ============================================================
    // UNSUPPORTED DESCRIPTION
    // ============================================================

    @Test
    void shouldRejectUnsupportedMaturityDescription() {

        bond.setMaturityDate(
                LocalDate.of(2030, 11, 9));

        bond.setMaturityDescription(
                "10% principal every year");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRepayments(
                        bond,
                        List.of(),
                        LocalDate.now()));

        System.out.println();
        System.out.println(
                "shouldRejectUnsupportedMaturityDescription -> "
                        + exception.getMessage());

        assertTrue(
                exception.getMessage()
                        .contains(
                                "Unsupported maturity description"));
    }

    // ============================================================
    // CASE INSENSITIVITY / WHITESPACE
    // ============================================================

    @Test
    void shouldAcceptAmortizationDescriptionWithDifferentCaseAndSpaces() {

        bond.setMaturityDate(
                LocalDate.of(2026, 11, 9));

        bond.setMaturityDescription(
                "  9/11/2024   TO   9/11/2026   "
                        + "( 10% EACH YEAR ) ");

        List<LocalDate> couponDates = List.of(
                LocalDate.of(2024, 11, 9),
                LocalDate.of(2025, 11, 9));

        List<PrincipalRepayment> result = service.generateRepayments(
                bond,
                couponDates,
                LocalDate.now());

        printRepayments(
                "shouldAcceptAmortizationDescriptionWithDifferentCaseAndSpaces",
                result);

        assertEquals(2, result.size());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(0).principalAmount());

        assertBigDecimalEquals(
                BigDecimal.TEN,
                result.get(1).principalAmount());
    }

    // ============================================================
    // PRINT HELPER
    // ============================================================

    private void printRepayments(
            String testName,
            List<PrincipalRepayment> repayments) {

        System.out.println();
        System.out.println(
                "============================================================");

        System.out.println(
                "TEST: " + testName);

        System.out.println(
                "============================================================");

        if (repayments == null || repayments.isEmpty()) {

            System.out.println(
                    "No principal repayments generated.");

            System.out.println(
                    "============================================================");

            return;
        }

        System.out.println(
                String.format(
                        "%-5s %-15s %-20s %-20s",
                        "#",
                        "DATE",
                        "PRINCIPAL",
                        "REMAINING"));

        System.out.println(
                "------------------------------------------------------------");

        for (int i = 0; i < repayments.size(); i++) {

            PrincipalRepayment repayment = repayments.get(i);

            System.out.println(
                    String.format(
                            "%-5d %-15s %-20s %-20s",
                            i + 1,
                            repayment.date(),
                            repayment.principalAmount(),
                            repayment.remainingPrincipal()));
        }

        System.out.println(
                "------------------------------------------------------------");

        System.out.println(
                "Total repayments: "
                        + repayments.size());

        BigDecimal totalPrincipal = repayments.stream()
                .map(PrincipalRepayment::principalAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);

        System.out.println(
                "Total principal repaid: "
                        + totalPrincipal);

        System.out.println(
                "============================================================");
    }

    // ============================================================
    // BIGDECIMAL ASSERTION HELPER
    // ============================================================

    private void assertBigDecimalEquals(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(
                0,
                expected.compareTo(actual),
                "Expected: " + expected
                        + " but was: " + actual);
    }
}
