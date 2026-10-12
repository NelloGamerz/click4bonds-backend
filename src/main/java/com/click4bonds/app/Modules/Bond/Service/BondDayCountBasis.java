package com.click4bonds.app.Modules.Bond.Service;

import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * The day-count basis a bond's yield is quoted on.
 *
 * <p>
 * One decision, kept in one place, because the yield and the price are the two
 * directions of the same discounting. If they disagreed about how long a year is,
 * pricing a bond from a yield and then solving the yield back out of that price
 * would not return the rate it started from.
 *
 * <p>
 * A Sovereign (government) security is quoted on European 30/360; every other
 * bond uses the platform's Actual/365.
 */
final class BondDayCountBasis {

    /** The rating that means "government security". */
    private static final String SOVEREIGN_RATING = "Sovereign";

    private BondDayCountBasis() {
    }

    /**
     * The basis for a bond.
     *
     * @param bond the bond; a {@code null} bond has no rating and so is not a
     *             Sovereign
     */
    static XirrCalculator.DayCountBasis of(Bond bond) {
        return of(bond == null ? null : bond.getRating());
    }

    /**
     * Whether a bond's rating means a government security.
     *
     * <p>
     * {@code Bond.rating} is free text, so the whole trimmed value is matched
     * case-insensitively: {@code "Sovereign"} and {@code "SOVEREIGN"} are one,
     * while {@code "Sovereign GOLD"} and {@code "AAA"} are not. Null is not a
     * Sovereign either — an unrated bond keeps the 365-day year it has always
     * been quoted on, which is the safe way round. The same rule decides which
     * deal-confirmation sheet a bond is lettered on (see
     * {@code DealConfirmationSheetStrategyFactory}).
     *
     * @param rating the bond's rating, or null when it has none
     */
    private static XirrCalculator.DayCountBasis of(String rating) {

        return isSovereign(rating)
                ? XirrCalculator.DayCountBasis.THIRTY_360
                : XirrCalculator.DayCountBasis.ACTUAL_365;
    }

    private static boolean isSovereign(String rating) {
        return rating != null && rating.trim().equalsIgnoreCase(SOVEREIGN_RATING);
    }
}
