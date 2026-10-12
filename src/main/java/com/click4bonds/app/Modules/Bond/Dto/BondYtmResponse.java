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
 * decimal is what a caller doing its own arithmetic works in. Both are read from
 * the one value stored on the bond, so they are the same number written two
 * ways rather than two readings that could drift apart.
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
 * A bond whose yield has never been calculated reports null for both yield
 * figures.
 *
 * @param isin            the bond's ISIN
 * @param bondName        the bond's name
 * @param calculationDate the date the projection started from
 * @param ytmPercentage   annual YTM as a percentage, to 4 decimals — the value
 *                        stored on the bond (e.g. 10.6947), or {@code null} when
 *                        it has never been calculated
 * @param ytmDecimal      the same annual YTM as a decimal (e.g. 0.106947), or
 *                        {@code null} wherever {@code ytmPercentage} is
 * @param price           the clean price stored on the bond, or {@code null}
 *                        when the bond has no price
 * @param accruedDays     days accrued at the calculation date, from the previous
 *                        coupon date — zero when none has accrued
 * @param accruedInterest accrued interest per 100 face value, at the platform's
 *                        full working precision, or zero when none has accrued
 * @param calculatedAt    when the bond's YTM was last computed and persisted
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
