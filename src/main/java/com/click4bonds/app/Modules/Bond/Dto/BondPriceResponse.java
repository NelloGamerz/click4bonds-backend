package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The price a single bond must trade at to yield a requested rate.
 *
 * <p>
 * The mirror of {@link BondYtmResponse}: that one reports the yield a price
 * implies, this one reports the price a yield implies. The yield is echoed back
 * in both representations the same way, because it is the input and a caller
 * checking a what-if needs to see the assumption it was answered on.
 *
 * <p>
 * No price is persisted by this calculation. A bond's price is market data,
 * maintained through the admin price update, and a lookup that silently
 * overwrote it with a derived figure would destroy the real one.
 *
 * @param isin                   the bond's ISIN
 * @param bondName               the bond's name
 * @param calculationDate        the settlement date the price is as of
 * @param ytmPercentage          the requested yield as a percentage, e.g.
 *                               {@code 12.5000}
 * @param ytmDecimal             the requested yield as a decimal, e.g.
 *                               {@code 0.125}
 * @param price                  clean price as a percentage of par, to 2
 *                               decimals
 * @param dirtyPrice             present value of the future receipts in rupees
 *                               per unit of face value
 * @param accruedInterest        interest accrued to the settlement date, in
 *                               rupees per unit; negative when the settlement is
 *                               in the record period of the coming coupon
 * @param accruedInterestCharged accrued interest the buyer pays: the accrual
 *                               itself, sign included — negative on an
 *                               ex-interest purchase
 * @param accruedDays            days the accrual covers; negative wherever the
 *                               accrual is
 * @param calculatedAt           when this price was computed
 */
public record BondPriceResponse(
        String isin,
        String bondName,
        LocalDate calculationDate,
        BigDecimal ytmPercentage,
        BigDecimal ytmDecimal,
        BigDecimal price,
        BigDecimal dirtyPrice,
        BigDecimal accruedInterest,
        BigDecimal accruedInterestCharged,
        long accruedDays,
        Instant calculatedAt
) {
}
