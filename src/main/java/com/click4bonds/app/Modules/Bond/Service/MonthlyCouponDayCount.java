package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * The year a monthly non-Sovereign coupon is measured in.
 *
 * <p>Twelve coupons a year are not twelve equal slices of an annual coupon:
 * January is thirty-one days long and February twenty-eight. A monthly
 * non-Sovereign bond therefore prices each coupon over the days its period
 * actually covers, against the length of the year that period belongs to:
 *
 * <pre>
 * outstanding principal x rate x days in the period
 * -------------------------------------------------
 *              100 x days in the year
 * </pre>
 *
 * <h2>Which year the period belongs to</h2>
 *
 * <p>The year the coupon is <em>paid</em> in — the year the period ends in, not
 * the year it opens in. A period ending in a leap year is measured over 366
 * days whatever part of it the leap day falls in, and a period ending in any
 * other year over 365.
 *
 * <p>A period is never split across the years it spans. For a monthly bond the
 * two years differ on exactly one period, the coupon paid in January, which
 * covers the previous December:
 *
 * <pre>
 * 17-Jan-2028 -> 17-Feb-2028   31 days, paid 2028   31 / 366
 * 17-Feb-2028 -> 17-Mar-2028   29 days, paid 2028   29 / 366
 * 17-Dec-2028 -> 17-Jan-2029   31 days, paid 2029   31 / 365
 * </pre>
 *
 * <p>The December days of that last period are therefore measured over 365, the
 * year the coupon is paid in, rather than the 366 of the year they fall in.
 * That is the source schedule's convention, and it is the one this class
 * implements.
 *
 * <h2>What this does not govern</h2>
 *
 * <p>A Sovereign is a government security quoted on 30/360 and keeps the flat
 * twelfth of an annual coupon. Every other frequency already pays on its
 * anniversary, where the per-period formula and the days in the period agree,
 * so it keeps the existing {@code rate / frequency} coupon.
 *
 * <p>The same day count governs the accrued interest a buyer pays for such a
 * bond: an accrual for part of a period is measured over the same year as the
 * coupon it accrues towards, because it is given the same period end.
 */
final class MonthlyCouponDayCount {

    /** The rating that means "government security". */
    static final String SOVEREIGN_RATING = "Sovereign";

    private static final int DAYS_IN_YEAR = 365;
    private static final int DAYS_IN_LEAP_YEAR = 366;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MonthlyCouponDayCount() {
    }

    /**
     * Whether this bond's coupons are priced over the actual days of their
     * periods.
     *
     * <p>Monthly frequency and any rating that is not a Sovereign. Null is not
     * a Sovereign, so an unrated monthly bond is measured this way.
     */
    static boolean appliesTo(Bond bond) {

        return bond != null
                && bond.getCouponFrequency() == CouponFrequency.MONTHLY
                && !isSovereign(bond.getRating());
    }

    /**
     * The whole coupon for the period running from {@code periodStart} up to
     * but not including {@code periodEnd}.
     */
    static BigDecimal coupon(
            BigDecimal principal,
            BigDecimal rate,
            LocalDate periodStart,
            LocalDate periodEnd,
            int scale) {

        return amount(
                principal,
                rate,
                ChronoUnit.DAYS.between(periodStart, periodEnd),
                periodStart,
                periodEnd,
                scale);
    }

    /**
     * The interest earned over {@code days} of the period running from
     * {@code periodStart} up to but not including {@code periodEnd}.
     *
     * <p>The period end sets the year, so an accrual for part of a period is
     * measured over the same year as the coupon it accrues towards.
     *
     * @param days the days of the period the interest is earned over, which is
     *             the whole period for a coupon and a part of it for an accrual
     */
    static BigDecimal amount(
            BigDecimal principal,
            BigDecimal rate,
            long days,
            LocalDate periodStart,
            LocalDate periodEnd,
            int scale) {

        if (days <= 0) {

            throw new IllegalArgumentException(
                    "Coupon period must cover at least one day: "
                            + periodStart + " to " + periodEnd);
        }

        return principal
                .multiply(rate)
                .multiply(BigDecimal.valueOf(days))
                .divide(
                        HUNDRED.multiply(
                                BigDecimal.valueOf(
                                        daysInYear(periodEnd))),
                        scale,
                        RoundingMode.HALF_UP);
    }

    /**
     * The length of the year the coupon is paid in: 366 for a leap year, 365
     * otherwise.
     *
     * @param periodEnd the date the coupon is paid on — the end of the period,
     *                  which is what decides the year
     */
    static int daysInYear(LocalDate periodEnd) {

        return Year.isLeap(periodEnd.getYear())
                ? DAYS_IN_LEAP_YEAR
                : DAYS_IN_YEAR;
    }

    /**
     * Whether a bond's rating means a government security.
     *
     * <p>{@code Bond.rating} is free text, so the whole trimmed value is
     * matched case-insensitively: {@code "Sovereign"} and {@code "SOVEREIGN"}
     * are one, while {@code "Sovereign GOLD"} and {@code "AAA"} are not. Null
     * is not a Sovereign either — an unrated bond is measured over the actual
     * days, which is the same choice {@code YtmCalculationServiceImpl} makes
     * when it leaves such a bond on the default day count.
     */
    private static boolean isSovereign(String rating) {

        return rating != null
                && rating.trim().equalsIgnoreCase(SOVEREIGN_RATING);
    }
}
