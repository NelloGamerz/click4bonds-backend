package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;

/**
 * A bond's price as of one settlement date, derived from a yield.
 *
 * <h2>Why several prices and not one</h2>
 *
 * <p>
 * The headline answer is {@link #price}: the clean price as a percentage of par,
 * which is the form {@code Bond.price} is stored in and the form a dealer quotes.
 * The rest of the record is the arithmetic that produced it, so a caller can see
 * which part of the money is the bond and which part is the coupon already run:
 *
 * <pre>
 * dirty price = present value of the receipts, discounted at the yield
 * clean price = dirty price - accrued interest charged
 * price       = clean price / face value x 100
 * </pre>
 *
 * <p>
 * The accrued interest appears twice because the two figures answer different
 * questions. {@link #accruedInterest} is what has accrued by the calendar;
 * {@link #accruedInterestCharged} is what the buyer actually pays for it. The
 * two are the same figure: the sign decides the treatment, so an ex-interest
 * purchase charges a negative accrual rather than none, and the clean price
 * rises by that amount. Reporting only the first would leave a caller unable to
 * reconcile the dirty price with the clean one.
 *
 * @param price                  clean price as a percentage of par, to 4
 *                               decimals, e.g. {@code 98.9400}
 * @param dirtyPrice             present value of the bond's future receipts in
 *                               rupees per unit of face value, at the platform's
 *                               full working precision
 * @param accruedInterest        interest accrued from the previous coupon date to
 *                               the settlement date, in rupees per unit;
 *                               negative in the record period of the coming
 *                               coupon
 * @param accruedInterestCharged accrued interest the buyer pays: the accrual
 *                               itself, sign included — positive on a
 *                               cum-interest purchase, negative on an
 *                               ex-interest one
 * @param accruedDays            days the accrual covers; negative wherever the
 *                               accrual is
 */
public record BondPrice(
        BigDecimal price,
        BigDecimal dirtyPrice,
        BigDecimal accruedInterest,
        BigDecimal accruedInterestCharged,
        long accruedDays
) {
}
