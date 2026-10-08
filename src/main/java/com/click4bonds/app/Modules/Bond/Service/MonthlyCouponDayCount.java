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
 * <p>The year the period <em>opens</em> in. For a monthly bond that is also the
 * year every one of the period's days falls in, with one exception: the period
 * paid in January, which opens in the previous December and covers only days of
 * that previous year. A period opening in a leap year is measured over 366 days
 * — every period of that year, not merely the one containing 29 February — and
 * a period opening in any other year over 365.
 *
 * <pre>
 * 1-Dec-2027 -> 1-Jan-2028   31 days, opens 2027   31 / 365
 * 1-Jan-2028 -> 1-Feb-2028   31 days, opens 2028   31 / 366
 * 1-Feb-2028 -> 1-Mar-2028   29 days, opens 2028   29 / 366
 * </pre>
 *
 * <h2>What this does not govern</h2>
 *
 * <p>A Sovereign is a government security quoted on 30/360 and keeps the flat
 * twelfth of an annual coupon. Every other frequency already pays on its
 * anniversary, where the per-period formula and the days in the period agree,
 * so it keeps the existing {@code rate / frequency} coupon.
 *
 * <p>The same day count governs the accrued interest a buyer pays for such a
 * bond, so an accrual inside a period is measured over the same year as the
 * coupon it accrues towards.
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
     * <p>The period sets the year, so an accrual for part of a period is
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
                                        daysInYear(periodStart))),
                        scale,
                        RoundingMode.HALF_UP);
    }

    /**
     * The length of the year the period opens in: 366 for a leap year, 365
     * otherwise.
     */
    static int daysInYear(LocalDate periodStart) {

        return Year.isLeap(periodStart.getYear())
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
