package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * {@code COUPDAYBS(valueDate, maturity, 2, 4)} — the G-Sec sheet's accrued days.
 *
 * <p>Worth its own test because nothing else in the application counts days this
 * way, and because a day out is a money error on a contractual document rather
 * than a cosmetic one: this figure multiplies the coupon to produce the interest
 * the customer is charged.</p>
 *
 * <p>The first case is taken from the committed template's own sample, where the
 * cell's cached result is 159 — so the convention is pinned against the
 * spreadsheet rather than against a restatement of it.</p>
 */
class GsecAccrualCalculatorTest {

    /** The committed sheet's sample G-Sec: 22 Apr / 22 Oct coupons, 2064 maturity. */
    private static final LocalDate GOI_MATURITY = LocalDate.of(2064, 4, 22);

    private final GsecAccrualCalculator calculator = new GsecAccrualCalculator();

    // =========================================================
    // THE TEMPLATE'S OWN SAMPLE
    // =========================================================

    @Test
    void reproducesTheDayCountTheTemplateItselfComputes() {

        /*
         * The sheet holds COUPDAYBS(C15,C19,2,4) over a 1 Oct 2026 value date and
         * a 22 Apr 2064 maturity, cached as 159. Semi-annual coupons on the 22nd,
         * European 30/360: Apr 22 to Oct 1 is six thirty-day months less the 21
         * days from the 22nd back to the 1st — 180 - 21.
         */
        assertEquals(159L,
                calculator.accruedDays(LocalDate.of(2026, 10, 1), GOI_MATURITY));
    }

    // =========================================================
    // WHERE THE COUPON PERIOD STARTS
    // =========================================================

    @Test
    void measuresFromThePreviousCouponDateNotTheMaturityDate() {

        /*
         * Both settlements sit in the same coupon period that opened on 22 Apr
         * 2026, and the count is from there — not from maturity, which is 38 years
         * away. Three thirty-day months is 90.
         */
        assertEquals(90L,
                calculator.accruedDays(LocalDate.of(2026, 7, 22), GOI_MATURITY));

        assertEquals(91L,
                calculator.accruedDays(LocalDate.of(2026, 7, 23), GOI_MATURITY));
    }

    @Test
    void stepsBackToTheCouponDateWhenSettlementIsEarlierInTheSameMonth() {

        /*
         * Counting months alone is not enough: 1 Oct and 22 Oct share a month, but
         * only the second has passed the coupon. Settlement on the 1st still
         * belongs to the period that opened in April and carries 159 days;
         * settlement on the 22nd opens a new period and has accrued nothing. A
         * month-only walk would give both the same answer.
         */
        assertEquals(159L,
                calculator.accruedDays(LocalDate.of(2026, 10, 1), GOI_MATURITY));

        assertEquals(0L,
                calculator.accruedDays(LocalDate.of(2026, 10, 22), GOI_MATURITY));
    }

    @Test
    void isZeroOnACouponDate() {

        assertEquals(0L,
                calculator.accruedDays(LocalDate.of(2026, 4, 22), GOI_MATURITY));
    }

    @Test
    void accruesOneDayLessThanAFullPeriodOnItsLastDay() {

        /*
         * A semi-annual period on the 22nd runs 22 Apr to 22 Oct. The last day
         * before the next coupon accrues 179 days, one short of the 180 the two
         * dates are apart — the coupon day itself opens the next period and resets
         * to zero, which the case above pins.
         */
        assertEquals(179L,
                calculator.accruedDays(LocalDate.of(2031, 4, 21), GOI_MATURITY));
    }

    // =========================================================
    // THE THIRTY-DAY MONTH
    // =========================================================

    @Test
    void treatsTheThirtyFirstAsTheThirtieth() {

        /*
         * The European 30/360 rule: a 31st counts as a 30th, so 31 Jan to 30 Mar
         * is exactly two thirty-day months. An actual-day count would say 58, and
         * the corporate letter's count says something else again — this is the
         * reason the two sheets cannot share one accrued-interest figure.
         */
        assertEquals(60L, calculator.accruedDays(
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2027, 1, 31)));
    }

    @Test
    void countsThirtyDayMonthsRatherThanCalendarOnes() {

        /*
         * The same period measured to the 1st rather than the 30th: 31 Jan to
         * 1 Mar is 60 days less the 29 the 30th-to-1st step costs, so 31. In
         * calendar days that span is only 29.
         */
        assertEquals(31L, calculator.accruedDays(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2027, 1, 31)));
    }

    @Test
    void keepsTheMaturityDayOfMonthWhenEarlierMonthsAreShorter() {

        /*
         * Coupon dates are derived from the maturity date rather than by repeated
         * subtraction, so a maturity on the 22nd stays on the 22nd all the way
         * back — including through Februaries. Same settlement, a maturity 38
         * years earlier, same 159.
         */
        assertEquals(159L, calculator.accruedDays(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2030, 4, 22)));
    }

    // =========================================================
    // A SECURITY THAT HAS MATURED
    // =========================================================

    @Test
    void accruesNothingOnOrAfterMaturity() {

        /*
         * A matured security has no accrual left. Measuring forward from maturity
         * would report interest on a bond that no longer exists — a month of it,
         * for a settlement a month late. Excel's COUPDAYBS returns #NUM! here; a
         * zero is the safer figure to print, and a trade settling after maturity is
         * a data error upstream rather than something this class can repair.
         */
        assertEquals(0L, calculator.accruedDays(
                LocalDate.of(2064, 6, 1), GOI_MATURITY));

        assertEquals(0L, calculator.accruedDays(GOI_MATURITY, GOI_MATURITY));
    }
}
