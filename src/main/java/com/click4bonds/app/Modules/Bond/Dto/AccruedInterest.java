package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;

/**
 * Accrued interest on a bond at a settlement date, with the days it accrued for.
 *
 * <p>
 * The days and the amount are returned together because the amount is defined by
 * the days: interest accrues as the coupon for the period times accrued days over
 * the period's length. Computing them in one place keeps a caller from reporting a
 * day count that does not produce the figure beside it.
 *
 * <p>
 * Both figures carry the same sign. They are positive while the buyer is
 * entitled to the coupon the period accrues towards, and negative when the
 * settlement falls in the record period of that coupon: the buyer will not
 * receive it, so the accrual is the part of it covering the stretch from
 * settlement to the coupon date, counted down to zero as the coupon approaches.
 *
 * @param days   days accrued at the calculation date, from the previous coupon
 *               date — negative in the record period of the coming coupon, and
 *               zero when nothing has accrued, including for a bond that pays no
 *               coupon
 * @param amount accrued interest per 100 face value, at the platform's full
 *               working precision; negative wherever {@code days} is
 */
public record AccruedInterest(
        long days,
        BigDecimal amount
) {
}
