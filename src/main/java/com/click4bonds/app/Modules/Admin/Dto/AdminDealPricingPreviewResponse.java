package com.click4bonds.app.Modules.Admin.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The price and yield a prospective deal would carry, and the value date they
 * were measured to.
 *
 * <p>Nothing here has been written to the bond or to a deal. The figures are
 * cached against the employee and the bond so the create request can pick them
 * up.</p>
 */
public record AdminDealPricingPreviewResponse(
        UUID bondId,
        String isin,
        String bondName,
        LocalDate valueDate,
        BigDecimal pricePerUnit,
        BigDecimal annualYtm,
        BigDecimal semiYtm) {
}
