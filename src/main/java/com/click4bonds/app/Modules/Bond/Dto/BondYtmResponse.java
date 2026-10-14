package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The yield to maturity of a single bond as of a calculation date.
 *
 * <p>
 * Both representations of the yield are returned because the two audiences
 * differ: the percentage is what the platform and the UI quote, while the decimal
 * is what a caller doing its own arithmetic works in. Both are read from the one
 * figure — whether that figure was stored on the bond or calculated for the
 * requested date — so they are the same number written two ways rather than two
 * readings that could drift apart.
 *
 * <p>
 * The accrued interest and the days it accrued for are returned alongside,
 * because they are the other half of what the settlement date means: the yield is
 * the return from here to maturity, and the accrual is what moves between buyer
 * and seller for the period already run — positive when the buyer is entitled to
 * the coming coupon, negative when the settlement is ex-interest and the buyer is
 * owed the stretch they will hold without being paid. Both are measured at the
 * same date, on the same coupon schedule, and both carry that sign, so a caller
 * showing one beside the other is not mixing two readings of the bond.
 *
 * <p>
 * A yield is only ever null for a bond whose yield has never been calculated and
 * whose calculation was not asked for — a yield requested for a date other than
 * today is always worked out, so the two figures come back populated.
 *
 * @param isin            the bond's ISIN
 * @param bondName        the bond's name
 * @param calculationDate the date the projection started from
 * @param ytmPercentage   annual YTM as a percentage, to 4 decimals
 *                        (e.g. 10.6947): the value stored on the bond when the
 *                        date is today, otherwise the value calculated for the
 *                        date asked for. {@code null} only when the bond has
 *                        never had a yield calculated
 * @param ytmDecimal      the same annual YTM as a decimal (e.g. 0.106947), or
 *                        {@code null} wherever {@code ytmPercentage} is
 * @param price           the clean price stored on the bond, or {@code null}
 *                        when the bond has no price
 * @param accruedDays     days accrued at the calculation date, from the previous
 *                        coupon date — zero when none has accrued
 * @param accruedInterest accrued interest per 100 face value, at the platform's
 *                        full working precision, or zero when none has accrued
 * @param calculatedAt    when the bond's YTM was last computed and persisted, or
 *                        {@code null} when this yield was not persisted at all —
 *                        which is the case for every date other than today, since
 *                        a yield for another date is only ever reported
 */
public record BondYtmResponse(
        String isin,
        String bondName,
        LocalDate calculationDate,
        BigDecimal ytmPercentage,
        BigDecimal ytmDecimal,
        BigDecimal price,
        long accruedDays,
        BigDecimal accruedInterest,
        Instant calculatedAt
) {
}
