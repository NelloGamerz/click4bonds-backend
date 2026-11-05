package com.click4bonds.app.Modules.Offer.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Everything a design needs to draw one offer, already resolved.
 *
 * <p>
 * The service computes these values once; a design only decides where they are
 * printed. Keeping the arithmetic out of the design is what lets a second design
 * be added without a second definition of price or yield.
 *
 * @param isin            the bond's ISIN
 * @param bondName        the bond's name
 * @param securityType    humanised security type, e.g. {@code Secured}, or null
 * @param rating          rating agency and grade joined, e.g. {@code CRISIL AAA}
 * @param couponRate      coupon rate as a percentage, e.g. {@code 8.45}
 * @param couponFrequency humanised frequency, e.g. {@code Half yearly}, or null
 * @param maturityDate    the bond's maturity date, or null
 * @param putCallDescription the bond's put/call text, or null
 * @param price           clean price derived or supplied, as a percentage of par
 * @param ytmPercentage   annual YTM as a percentage
 * @param asOf            the settlement date both figures are measured at
 */
public record OfferDetails(
        String isin,
        String bondName,
        String securityType,
        String rating,
        BigDecimal couponRate,
        String couponFrequency,
        LocalDate maturityDate,
        String putCallDescription,
        BigDecimal price,
        BigDecimal ytmPercentage,
        LocalDate asOf
) {
}
