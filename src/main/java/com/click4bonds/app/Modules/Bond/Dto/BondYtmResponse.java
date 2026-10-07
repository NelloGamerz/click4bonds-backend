package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The yield to maturity of a single bond as of a calculation date.
 *
 * <p>
 * Both representations of the yield are returned because the two audiences
 * differ: the percentage is what the persisted bond and the UI show, while the
 * decimal is the unrounded figure the XIRR solver produced, for callers that need
 * to do their own arithmetic without losing precision to the display rounding.
 *
 * <p>
 * The accrued interest and the days it accrued for are returned alongside,
 * because they are the other half of what the settlement date means: the yield is
 * the return from here to maturity, and the accrual is what the seller is owed
 * for the period already run. Both are measured at the same date, on the same
 * coupon schedule, so a caller showing one beside the other is not mixing two
 * readings of the bond.
 *
 * @param isin            the bond's ISIN
 * @param bondName        the bond's name
 * @param calculationDate the date the projection started from
 * @param ytmPercentage   annual YTM as a percentage, rounded to 4 decimals —
 *                        the value persisted on the bond (e.g. 10.6947)
 * @param ytmDecimal      annual YTM as a decimal, unrounded (e.g. 0.106947)
 * @param price           the clean price the calculation used, or {@code null}
 *                        when the bond has no price
 * @param accruedDays     days accrued at the calculation date, from the previous
 *                        coupon date — zero when none has accrued
 * @param accruedInterest accrued interest per 100 face value, at the platform's
 *                        full working precision, or zero when none has accrued
 * @param calculatedAt    when this YTM was computed and persisted
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
