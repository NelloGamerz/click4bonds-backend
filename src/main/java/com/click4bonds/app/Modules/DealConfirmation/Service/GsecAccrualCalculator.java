package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

/**
 * Accrued days on the G-Sec letter's own convention.
 *
 * <p>This is {@code COUPDAYBS(valueDate, maturity, 2, 4)} — the number of days
 * from the start of the current coupon period to settlement, with
 * <strong>semi-annual</strong> coupons and the <strong>European 30/360</strong>
 * basis. It exists because the G-Sec sheet's accrued interest is not the figure
 * the rest of the platform uses: the corporate letter takes accrued interest from
 * the bond's own schedule on an actual/actual count, while this sheet's rule is
 * {@code quantum x coupon x days / 360}.</p>
 *
 * <p><strong>Two conventions, side by side in one formula.</strong> The days come
 * from a 30/360 count, and the interest then divides by 360 with those
 * thirty-day-month days. That is what the sheet does and it is reproduced rather
 * than corrected — the two are not interchangeable with an actual/360 count, and
 * "fixing" one without the other would change the money.</p>
 *
 * <p><strong>Why it is computed here rather than read from the sheet.</strong> The
 * template holds these as live formulas. Leaving them live would print the
 * template's cached values for whichever deal it was last saved from unless the
 * receiving application recalculated on open, and the corporate letter already
 * settled the opposite way: the platform's figures win and are written as literals
 * (see {@code XlsxTemplateWriter}).</p>
 *
 * <p><strong>Limits, deliberately accepted.</strong> Coupon dates are taken to fall
 * on the maturity date's day of every sixth month, clamped to the month's length —
 * so a maturity on the 31st yields 30th/28th-based dates in short months. Excel's
 * {@code COUPDAYBS} has end-of-month rules that this does not reproduce. Government
 * securities mature on a fixed day (22nd, 12th and so on), where the two agree, and
 * a G-Sec with a month-end maturity would need this revisited. The day count is
 * likewise the European variant, not the US one.</p>
 */
@Component
public class GsecAccrualCalculator {

    /** Semi-annual coupons, the {@code 2} in {@code COUPDAYBS(..., 2, 4)}. */
    private static final int MONTHS_PER_COUPON_PERIOD = 6;

    private static final int DAYS_IN_THIRTY_DAY_MONTH = 30;

    private static final int DAYS_IN_THREE_SIXTY_YEAR = 360;

    /**
     * Days of accrued interest at settlement, on this sheet's convention.
     *
     * @param valueDate    settlement date — {@code C15} on the sheet
     * @param maturityDate the security's maturity — {@code C19}
     * @return days from the previous coupon date to the value date, never
     *         negative
     */
    public long accruedDays(LocalDate valueDate, LocalDate maturityDate) {

        /*
         * A matured security has nothing left to accrue. Without this the coupon
         * walk below stops at maturity and measures forward from it, so a
         * settlement a month after maturity would report a month of interest on a
         * bond that no longer exists. Excel's COUPDAYBS returns #NUM! here; a zero
         * is the safer thing to print, and a trade settling after maturity is a
         * data error upstream rather than something this class can repair.
         */
        if (!valueDate.isBefore(maturityDate)) {
            return 0L;
        }

        return daysBetween(previousCouponDate(valueDate, maturityDate), valueDate);
    }

    /**
     * The coupon date on or before the value date.
     *
     * <p>Found by counting whole coupon periods back from maturity, so the day of
     * the month always comes from the maturity date rather than drifting through
     * repeated subtraction — a coupon schedule is anchored on maturity, not on the
     * settlement date.</p>
     *
     * <p>Settlement on or after maturity returns the maturity date itself, which
     * makes the accrued days zero rather than negative. A mature security has no
     * further accrual, and printing a negative one would be worse than printing
     * nothing.</p>
     */
    private LocalDate previousCouponDate(LocalDate valueDate, LocalDate maturityDate) {

        long periods = ChronoUnit.MONTHS
                .between(YearMonth.from(valueDate), YearMonth.from(maturityDate))
                / MONTHS_PER_COUPON_PERIOD;

        LocalDate couponDate = maturityDate.minusMonths(
                periods * MONTHS_PER_COUPON_PERIOD);

        /*
         * The month count alone is not enough: within a matching month the day of
         * the month can still put the coupon date after settlement. 1 Oct against a
         * 22 Oct coupon lands in the same month and one period too late, so step
         * back again.
         */
        while (couponDate.isAfter(valueDate)) {

            periods++;
            couponDate = maturityDate.minusMonths(periods * MONTHS_PER_COUPON_PERIOD);
        }

        return couponDate;
    }

    /**
     * The European 30/360 day count: every month is thirty days, every year three
     * hundred and sixty, and days 31 are treated as 30.
     *
     * <p>Not the US/NASD variant, which adjusts for the end of February as well —
     * {@code COUPDAYBS(..., 4)} names this one.</p>
     */
    private long daysBetween(LocalDate from, LocalDate to) {

        int fromDay = Math.min(from.getDayOfMonth(), DAYS_IN_THIRTY_DAY_MONTH);
        int toDay = Math.min(to.getDayOfMonth(), DAYS_IN_THIRTY_DAY_MONTH);

        return (to.getYear() - from.getYear()) * (long) DAYS_IN_THREE_SIXTY_YEAR
                + (to.getMonthValue() - from.getMonthValue())
                        * (long) DAYS_IN_THIRTY_DAY_MONTH
                + (toDay - fromDay);
    }
}
