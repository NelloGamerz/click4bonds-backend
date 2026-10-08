package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * The year a monthly non-Sovereign coupon is measured in.
 *
 * <p>The one fact that separates the two years is the leap day: 29 February
 * belongs to exactly one period in a leap year, and only that period is
 * measured over 366 days.
 */
class MonthlyCouponDayCountTest {

    private static final int SCALE = 10;

    // ======================
    // Which bonds it governs
    // ======================

    @Test
    void governsMonthlyBondsThatAreNotSovereigns() {

        assertTrue(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.MONTHLY, null)));
        assertTrue(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.MONTHLY, "AAA")));

        /*
         * The whole trimmed value is matched: "Sovereign GOLD" is a credit
         * label rather than a government security.
         */
        assertTrue(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.MONTHLY, "Sovereign GOLD")));
    }

    @Test
    void leavesSovereignsAndEveryOtherFrequencyAlone() {

        assertFalse(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.MONTHLY, "Sovereign")));
        assertFalse(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.MONTHLY, "  SOVEREIGN  ")));

        assertFalse(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.QUARTERLY, null)));
        assertFalse(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.HALF_YEARLY, null)));
        assertFalse(MonthlyCouponDayCount.appliesTo(
                bond(CouponFrequency.YEARLY, null)));

        assertFalse(MonthlyCouponDayCount.appliesTo(null));
    }

    // ======================
    // The year itself
    // ======================

    @Test
    void measuresEveryCouponPaidInALeapYearOverThreeHundredAndSixtySixDays() {

        /*
         * The whole leap year, not merely the period the leap day falls in:
         * January's 2028 coupon has no part of the extra day in it and is still
         * measured over 366.
         */
        assertEquals(366, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2028, 1, 1)));
        assertEquals(366, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2028, 2, 1)));
        assertEquals(366, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2028, 12, 1)));
    }

    @Test
    void measuresEveryCouponPaidInANormalYearOverThreeHundredAndSixtyFiveDays() {

        assertEquals(365, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2027, 1, 1)));
        assertEquals(365, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2027, 2, 1)));
        assertEquals(365, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2029, 1, 17)));
    }

    // ======================
    // The year a period is paid in
    // ======================

    @Test
    void measuresTheCrossYearPeriodOverTheYearItIsPaidIn() {

        /*
         * 17-Dec-2028 -> 17-Jan-2029 is 31 days of December paid in January.
         * The year the coupon is paid in is 2029, which is not a leap year, so
         * the period is measured over 365 — 31/365, not 31/366:
         *
         * 40 × 11 × 31 / (100 × 365) = 0.3736986301
         */
        assertAmountEquals(
                "0.3736986301",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("40"),
                        new BigDecimal("11"),
                        LocalDate.of(2028, 12, 17),
                        LocalDate.of(2029, 1, 17),
                        SCALE));
    }

    @Test
    void doesNotSplitTheCrossYearPeriodAtTheCalendarYearBoundary() {

        /*
         * Splitting the period would be 14/366 + 17/365 = 0.0848267086, giving
         * 0.3732375178 rather than the 0.3736986301 the same period pays when
         * it is measured whole over the 365 days of the year it is paid in.
         */
        BigDecimal splitFraction = new BigDecimal("14")
                .divide(new BigDecimal("366"), SCALE, RoundingMode.HALF_UP)
                .add(new BigDecimal("17").divide(new BigDecimal("365"), SCALE, RoundingMode.HALF_UP));

        assertAmountEquals("0.0848267086", splitFraction);

        BigDecimal splitCoupon = new BigDecimal("40")
                .multiply(new BigDecimal("11"))
                .multiply(splitFraction)
                .divide(new BigDecimal("100"), SCALE, RoundingMode.HALF_UP);

        assertAmountEquals("0.3732375178", splitCoupon);

        assertAmountEquals(
                "0.3736986301",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("40"),
                        new BigDecimal("11"),
                        LocalDate.of(2028, 12, 17),
                        LocalDate.of(2029, 1, 17),
                        SCALE));
    }

    @Test
    void measuresAJanuaryToFebruaryPeriodOfALeapYearOverThreeHundredAndSixtySixDays() {

        /*
         * 17-Jan-2028 -> 17-Feb-2028 is paid in 2028, a leap year, so it is
         * measured over 366 even though the leap day is outside it.
         *
         * 40 × 11 × 31 / (100 × 366) = 0.3726775956
         */
        assertAmountEquals(
                "0.3726775956",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("40"),
                        new BigDecimal("11"),
                        LocalDate.of(2028, 1, 17),
                        LocalDate.of(2028, 2, 17),
                        SCALE));
    }

    @Test
    void measuresAFebruaryToMarchPeriodOfALeapYearOverThreeHundredAndSixtySixDays() {

        /*
         * The 29-day February period, paid in the leap year:
         *
         * 40 × 11 × 29 / (100 × 366) = 0.3486338798
         */
        assertAmountEquals(
                "0.3486338798",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("40"),
                        new BigDecimal("11"),
                        LocalDate.of(2028, 2, 17),
                        LocalDate.of(2028, 3, 17),
                        SCALE));
    }

    @Test
    void measuresAJanuaryToFebruaryPeriodOfANormalYearOverThreeHundredAndSixtyFiveDays() {

        /*
         * 40 × 11 × 31 / (100 × 365) = 0.3736986301
         */
        assertAmountEquals(
                "0.3736986301",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("40"),
                        new BigDecimal("11"),
                        LocalDate.of(2029, 1, 17),
                        LocalDate.of(2029, 2, 17),
                        SCALE));
    }

    // ======================
    // The money
    // ======================

    @Test
    void pricesAThirtyOneDayMonthPaidInAThreeHundredAndSixtyFiveDayYear() {

        assertAmountEquals(
                "1.0191780822",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        LocalDate.of(2028, 12, 1),
                        LocalDate.of(2029, 1, 1),
                        SCALE));
    }

    @Test
    void pricesTheLeapPeriodOverTheWholeThreeHundredAndSixtySixDayYear() {

        assertAmountEquals(
                "0.9508196721",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        LocalDate.of(2028, 2, 1),
                        LocalDate.of(2028, 3, 1),
                        SCALE));
    }

    @Test
    void pricesAPartOfThePeriodOverTheSameYearTheWholePeriodUses() {

        /*
         * Seven days of February's 29-day period are measured over the same
         * 366-day year the coupon itself is:
         *
         * 100 × 12 × 7 / (100 × 366) = 0.2295081967
         *
         * Measuring the part over 365 days would have it accrue towards a
         * coupon priced in a different year.
         */
        assertAmountEquals(
                "0.2295081967",
                MonthlyCouponDayCount.amount(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        7,
                        LocalDate.of(2028, 2, 1),
                        LocalDate.of(2028, 3, 1),
                        SCALE));
    }

    @Test
    void usesTheOutstandingPrincipalRatherThanTheFaceValue() {

        /*
         * 95.25 of principal over 31 days paid in 2029, a normal year:
         *
         * 95.25 × 12 × 31 / (100 × 365) = 0.9707671233
         */
        assertAmountEquals(
                "0.9707671233",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("95.25"),
                        new BigDecimal("12"),
                        LocalDate.of(2028, 12, 1),
                        LocalDate.of(2029, 1, 1),
                        SCALE));
    }

    @Test
    void rejectsAPeriodThatCoversNoDays() {

        assertThrows(
                IllegalArgumentException.class,
                () -> MonthlyCouponDayCount.amount(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        0,
                        LocalDate.of(2028, 2, 1),
                        LocalDate.of(2028, 3, 1),
                        SCALE));
    }

    private static Bond bond(CouponFrequency frequency, String rating) {

        Bond bond = new Bond();
        bond.setCouponFrequency(frequency);
        bond.setRating(rating);

        return bond;
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Expected " + expected + " but was " + actual);
    }
}
