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
 * @param days   days accrued at the calculation date, from the previous coupon
 *               date — zero when nothing has accrued, including for a bond that
 *               pays no coupon
 * @param amount accrued interest per 100 face value, at the platform's full
 *               working precision
 */
public record AccruedInterest(
        long days,
        BigDecimal amount
) {
}
