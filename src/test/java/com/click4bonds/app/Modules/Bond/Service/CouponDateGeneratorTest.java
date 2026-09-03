package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Models.Bond;

class CouponDateGeneratorTest {

    private CouponDateGenerator couponDateGenerator;

    @BeforeEach
    void setUp() {
        couponDateGenerator = new CouponDateGenerator();
    }

    // ============================================================
    // SEMI ANNUAL
    // ============================================================

    @Test
    void shouldGenerateSemiAnnualDates() {

        Bond bond = createBond(
                "09/02-09/08",
                LocalDate.of(2029, 8, 9));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldGenerateSemiAnnualDates -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2027, 2, 9),
                LocalDate.of(2027, 8, 9),
                LocalDate.of(2028, 2, 9),
                LocalDate.of(2028, 8, 9),
                LocalDate.of(2029, 2, 9),
                LocalDate.of(2029, 8, 9));

        assertEquals(expected, result);
    }

    @Test
    void shouldGenerateSemiAnnualDatesForAprilOctober() {

        Bond bond = createBond(
                "04/04-04/10",
                LocalDate.of(2030, 10, 4));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldGenerateSemiAnnualDatesForAprilOctober -> "
                        + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2027, 4, 4),
                LocalDate.of(2027, 10, 4),
                LocalDate.of(2028, 4, 4),
                LocalDate.of(2028, 10, 4),
                LocalDate.of(2029, 4, 4),
                LocalDate.of(2029, 10, 4),
                LocalDate.of(2030, 4, 4),
                LocalDate.of(2030, 10, 4));

        assertEquals(expected, result);
    }

    @Test
    void shouldNotIncludePastSemiAnnualDates() {

        Bond bond = createBond(
                "09/02-09/08",
                LocalDate.of(2027, 8, 9));

        LocalDate calculationDate = LocalDate.of(2027, 2, 9);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldNotIncludePastSemiAnnualDates -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2027, 8, 9));

        assertEquals(expected, result);
    }

    // ============================================================
    // ANNUAL
    // ============================================================

    @Test
    void shouldGenerateAnnualDates() {

        Bond bond = createBond(
                "15/10 Ann",
                LocalDate.of(2030, 10, 15));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldGenerateAnnualDates -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2027, 10, 15),
                LocalDate.of(2028, 10, 15),
                LocalDate.of(2029, 10, 15),
                LocalDate.of(2030, 10, 15));

        assertEquals(expected, result);
    }

    @Test
    void shouldHandleAnnualDateCaseInsensitively() {

        Bond bond = createBond(
                "15/10 ann",
                LocalDate.of(2028, 10, 15));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldHandleAnnualDateCaseInsensitively -> "
                        + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2027, 10, 15),
                LocalDate.of(2028, 10, 15));

        assertEquals(expected, result);
    }

    @Test
    void shouldNotIncludeAnnualCouponAfterMaturity() {

        Bond bond = createBond(
                "15/10 Ann",
                LocalDate.of(2028, 5, 10));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldNotIncludeAnnualCouponAfterMaturity -> "
                        + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2027, 10, 15));

        assertEquals(expected, result);
    }

    // ============================================================
    // MONTHLY
    // ============================================================

    @Test
    void shouldGenerateMonthlyDates() {

        Bond bond = createBond(
                "15th of Every Month",
                LocalDate.of(2027, 3, 15));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldGenerateMonthlyDates -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2027, 1, 15),
                LocalDate.of(2027, 2, 15),
                LocalDate.of(2027, 3, 15));

        assertEquals(expected, result);
    }

    @Test
    void shouldGenerateMonthlyDatesFor23rd() {

        Bond bond = createBond(
                "23rd of every month",
                LocalDate.of(2027, 2, 23));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldGenerateMonthlyDatesFor23rd -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 10, 23),
                LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 12, 23),
                LocalDate.of(2027, 1, 23),
                LocalDate.of(2027, 2, 23));

        assertEquals(expected, result);
    }

    @Test
    void shouldHandle31stOfEveryMonth() {

        Bond bond = createBond(
                "31st of every month",
                LocalDate.of(2027, 4, 30));

        LocalDate calculationDate = LocalDate.of(2027, 1, 1);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldHandle31stOfEveryMonth -> " + result);

        /*
         * 31st:
         *
         * January -> 31
         * February -> 28
         * March -> 31
         * April -> 30
         */

        List<LocalDate> expected = List.of(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30));

        assertEquals(expected, result);
    }

    @Test
    void shouldHandle31stInLeapYear() {

        Bond bond = createBond(
                "31st of every month",
                LocalDate.of(2028, 3, 31));

        LocalDate calculationDate = LocalDate.of(2028, 1, 1);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldHandle31stInLeapYear -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2028, 1, 31),
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 31));

        assertEquals(expected, result);
    }

    // ============================================================
    // WHITESPACE / NORMALIZATION
    // ============================================================

    @Test
    void shouldHandleExtraWhitespace() {

        Bond bond = createBond(
                "  15/10    Ann  ",
                LocalDate.of(2028, 10, 15));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldHandleExtraWhitespace -> " + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2027, 10, 15),
                LocalDate.of(2028, 10, 15));

        assertEquals(expected, result);
    }

    // ============================================================
    // MATURITY
    // ============================================================

    @Test
    void shouldIncludeCouponOnMaturityDate() {

        Bond bond = createBond(
                "15/10 Ann",
                LocalDate.of(2028, 10, 15));

        LocalDate calculationDate = LocalDate.of(2028, 1, 1);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldIncludeCouponOnMaturityDate -> " + result);

        assertEquals(
                LocalDate.of(2028, 10, 15),
                result.get(result.size() - 1));
    }

    @Test
    void shouldNotGenerateDatesAfterMaturity() {

        Bond bond = createBond(
                "15/10 Ann",
                LocalDate.of(2027, 5, 1));

        LocalDate calculationDate = LocalDate.of(2026, 8, 24);

        List<LocalDate> result = couponDateGenerator.generate(
                bond,
                calculationDate);

        System.out.println(
                "shouldNotGenerateDatesAfterMaturity -> "
                        + result);

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 10, 15));

        assertEquals(expected, result);
    }

    // ============================================================
    // INVALID INPUT
    // ============================================================

    @Test
    void shouldRejectNullBond() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        null,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectNullBond -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectNullCalculationDate() {

        Bond bond = createBond(
                "15/10 Ann",
                LocalDate.of(2028, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        null));

        System.out.println(
                "shouldRejectNullCalculationDate -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectMissingIpDateDescription() {

        Bond bond = createBond(
                null,
                LocalDate.of(2028, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectMissingIpDateDescription -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectBlankIpDateDescription() {

        Bond bond = createBond(
                "   ",
                LocalDate.of(2028, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectBlankIpDateDescription -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectMissingMaturityDate() {

        Bond bond = createBond(
                "15/10 Ann",
                null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectMissingMaturityDate -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectMaturityBeforeCalculationDate() {

        Bond bond = createBond(
                "15/10 Ann",
                LocalDate.of(2025, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectMaturityBeforeCalculationDate -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectUnsupportedIpDateFormat() {

        Bond bond = createBond(
                "15-10-2027",
                LocalDate.of(2028, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectUnsupportedIpDateFormat -> PASSED: "
                        + exception.getMessage());

        assertEquals(
                "Unsupported IP date description: 15-10-2027",
                exception.getMessage());
    }

    @Test
    void shouldRejectInvalidMonth() {

        Bond bond = createBond(
                "15/13 Ann",
                LocalDate.of(2028, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectInvalidMonth -> PASSED: "
                        + exception.getMessage());
    }

    @Test
    void shouldRejectInvalidDay() {

        Bond bond = createBond(
                "32/10 Ann",
                LocalDate.of(2028, 10, 15));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponDateGenerator.generate(
                        bond,
                        LocalDate.of(2026, 8, 24)));

        System.out.println(
                "shouldRejectInvalidDay -> PASSED: "
                        + exception.getMessage());
    }

    // ============================================================
    // HELPER
    // ============================================================

    private Bond createBond(
            String ipDateDescription,
            LocalDate maturityDate) {
        return Bond.builder()
                .ipDateDescription(ipDateDescription)
                .maturityDate(maturityDate)
                .build();
    }
}