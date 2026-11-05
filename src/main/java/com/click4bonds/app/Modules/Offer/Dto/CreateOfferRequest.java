package com.click4bonds.app.Modules.Offer.Dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A request to draw an offer for one bond.
 *
 * <p>
 * The caller may supply one half of the price/yield pair and the server derives
 * the other: send {@code ytm} and the price is calculated, send {@code price}
 * and the yield is. Sending both is rejected — an offer that names two figures
 * at once no longer says which one it was struck at. Sending neither draws the
 * offer from the price and yield already stored on the bond.
 */
public record CreateOfferRequest(

        /** ISIN of the bond the offer is for; matched case-insensitively. */
        @NotBlank(message = "ISIN is required")
        @Size(max = 12, message = "ISIN must be at most 12 characters")
        String isin,

        /**
         * Annual yield to maturity as a percentage, e.g. {@code 12.5} for 12.5%.
         * Mutually exclusive with {@link #price}.
         */
        BigDecimal ytm,

        /**
         * Clean price as a percentage of par, e.g. {@code 98.94}. Mutually
         * exclusive with {@link #ytm}.
         */
        @Positive(message = "Price must be greater than 0")
        BigDecimal price,

        /**
         * Which design to draw. Optional: omitted means the default design,
         * currently {@code DIWALI}.
         */
        String design
) {
}
