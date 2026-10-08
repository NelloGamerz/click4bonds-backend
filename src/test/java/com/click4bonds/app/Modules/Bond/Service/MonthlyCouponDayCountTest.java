package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
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
    void measuresEveryPeriodOpeningInALeapYearOverThreeHundredAndSixtySixDays() {

        /*
         * The whole leap year, not merely the period the leap day falls in:
         * January's 2028 period has no part of the extra day in it and is still
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
    void measuresEveryPeriodOpeningInANormalYearOverThreeHundredAndSixtyFiveDays() {

        assertEquals(365, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2027, 1, 1)));
        assertEquals(365, MonthlyCouponDayCount.daysInYear(
                LocalDate.of(2027, 2, 1)));
    }

    @Test
    void measuresTheJanuaryPeriodOverTheYearItOpensInRatherThanTheYearItIsPaid() {

        /*
         * The period paid in January opens in the previous December, and every
         * one of its days falls in that previous year. A January 2028 coupon
         * opening in December 2027 is therefore measured over 365 days, while
         * the February coupon that follows it opens in 2028 and uses 366.
         */
        assertAmountEquals(
                "1.0191780822",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        LocalDate.of(2027, 12, 1),
                        LocalDate.of(2028, 1, 1),
                        SCALE));

        assertAmountEquals(
                "1.0163934426",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        LocalDate.of(2028, 1, 1),
                        LocalDate.of(2028, 2, 1),
                        SCALE));
    }

    // ======================
    // The money
    // ======================

    @Test
    void pricesAThirtyOneDayMonthOfAThreeHundredAndSixtyFiveDayYear() {

        assertAmountEquals(
                "1.0191780822",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("100"),
                        new BigDecimal("12"),
                        LocalDate.of(2027, 12, 1),
                        LocalDate.of(2028, 1, 1),
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

        assertAmountEquals(
                "0.9707671233",
                MonthlyCouponDayCount.coupon(
                        new BigDecimal("95.25"),
                        new BigDecimal("12"),
                        LocalDate.of(2027, 12, 1),
                        LocalDate.of(2028, 1, 1),
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
