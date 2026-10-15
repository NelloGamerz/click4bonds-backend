package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.CalculationType;

/**
 * The half of a bond's price/yield pair that the platform derived, chosen by the
 * bond's own {@link CalculationType}.
 *
 * <p>
 * {@link BondYtmResponse} answers one fixed question — what does this bond yield?
 * — and {@link BondPriceResponse} answers the other. This one answers whichever
 * of the two the bond itself says is the open question: a bond created
 * {@link CalculationType#BY_YTM} was given a yield and is missing its price, so
 * the price is what comes back derived; a bond created
 * {@link CalculationType#BY_PRICE} is the mirror of that.
 *
 * <p>
 * The fields are those of {@link BondYtmResponse} with the resolution that chose
 * them. That is deliberate: two figures alone would leave a caller guessing which
 * of the pair it is being handed, and which of them it may treat as given.
 * {@link #calculationType} names both answers at once — which figure was the
 * input, and therefore which one was worked out for this response.
 *
 * <p>
 * Nothing is persisted. A bond's price is market data and its yield is the
 * platform's quoted figure; both are settled when the bond is created, priced or
 * updated, and a read that silently rewrote one of them would move a number the
 * platform quotes. A caller wanting the derived figure stored should go through
 * the admin create or update instead.
 *
 * @param isin            the bond's ISIN
 * @param bondName        the bond's name
 * @param calculationDate the settlement date the derived figure and the accrual
 *                        are measured to
 * @param calculationType which half of the pair was the source, and so which half
 *                        was derived here: the bond's own type, or the type
 *                        inferred from the figures it holds when it declares
 *                        none. Never {@code null}
 * @param ytmPercentage   annual YTM as a percentage, to 4 decimals (e.g.
 *                        {@code 10.6947}): the bond's stored yield under
 *                        {@link CalculationType#BY_YTM}, otherwise the yield
 *                        derived from its price for this date
 * @param ytmDecimal      the same annual YTM as a decimal (e.g. {@code 0.106947})
 * @param price           clean price as a percentage of par, to 2 decimals: the
 *                        bond's stored price under
 *                        {@link CalculationType#BY_PRICE}, otherwise the price
 *                        derived from its yield for this date
 * @param accruedDays     days accrued at the calculation date, from the previous
 *                        coupon date — zero when none has accrued
 * @param accruedInterest accrued interest per 100 face value, at the platform's
 *                        full working precision, or zero when none has accrued
 * @param calculatedAt    when the stored yield was computed and persisted, or
 *                        {@code null} when the yield in this response was derived
 *                        for this request and never stored — which is the case
 *                        for every {@link CalculationType#BY_PRICE} bond
 */
public record BondCalculationResponse(
        String isin,
        String bondName,
        LocalDate calculationDate,
        CalculationType calculationType,
        BigDecimal ytmPercentage,
        BigDecimal ytmDecimal,
        BigDecimal price,
        long accruedDays,
        BigDecimal accruedInterest,
        Instant calculatedAt
) {
}
