package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The interest figures a confirmation letter prints, computed once at deal time.
 *
 * <p>Computed inside the transaction that creates the deal and carried on the
 * document snapshot afterwards, because the document step runs after that
 * transaction has committed and must not read the database or touch a lazy
 * association. The services that produce these numbers take a {@code Bond}
 * <em>entity</em>, so the results travel rather than the inputs.</p>
 *
 * @param previousCouponDate the coupon date on or before the value date — the
 *                           letter's "Last Interest Payment Date". Null when the
 *                           bond's coupon schedule could not be resolved.
 * @param accruedDays        days from {@code previousCouponDate} to the value
 *                           date. Zero when there is no previous coupon.
 * @param accruedInterestPerHundredFace accrued interest for one bond of face
 *                           value 100, so the letter multiplies by quantity
 *                           rather than the calculator knowing the quantity.
 */
public record DealAccrual(
        LocalDate previousCouponDate,
        long accruedDays,
        BigDecimal accruedInterestPerHundredFace
) {
}
