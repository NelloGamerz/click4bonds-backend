package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The yield to maturity of a single bond as of a calculation date.
 *
 * <p>
 * Both representations are returned because the two audiences differ: the
 * percentage is what the persisted bond and the UI show, while the decimal is
 * the unrounded figure the XIRR solver produced, for callers that need to do
 * their own arithmetic without losing precision to the 2-decimal display
 * rounding.
 *
 * @param isin            the bond's ISIN
 * @param bondName        the bond's name
 * @param calculationDate the date the projection started from
 * @param ytmPercentage   annual YTM as a percentage, rounded to 2 decimals —
 *                        the value persisted on the bond (e.g. 10.69)
 * @param ytmDecimal      annual YTM as a decimal, unrounded (e.g. 0.106947)
 * @param price           the clean price the calculation used, or {@code null}
 *                        when the bond has no price
 * @param calculatedAt    when this YTM was computed and persisted
 */
public record BondYtmResponse(
        String isin,
        String bondName,
        LocalDate calculationDate,
        BigDecimal ytmPercentage,
        BigDecimal ytmDecimal,
        BigDecimal price,
        Instant calculatedAt
) {
}
