package com.click4bonds.app.Modules.Admin.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * A request to price a bond for a deal that does not exist yet.
 *
 * <p>Exactly one of {@link #price} or {@link #annualYtm} is supplied; the other
 * is calculated. The result is cached for the employee's next create request and
 * never written to the bond.</p>
 */
public record PreviewDealPricingRequest(

        @NotNull(message = "Bond id is required")
        UUID bondId,

        /**
         * Clean price per unit as a percentage of par (e.g. {@code 98.94}), or
         * null when the caller supplied a yield instead.
         */
        BigDecimal price,

        /** Annual yield as a percentage (e.g. {@code 12.5}), or null. */
        BigDecimal annualYtm,

        /**
         * The date the deal will be struck, which fixes the value date the
         * calculation measures to. Null means today.
         */
        LocalDate dealDate) {
}
