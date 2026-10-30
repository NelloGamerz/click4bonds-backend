package com.click4bonds.app.Modules.Admin.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The outcome of pricing one deal: both figures, as saved on the deal.
 *
 * @param calculationDate the settlement date the figures were measured to — the
 *                        deal's value date
 */
public record AdminDealCalculationResponse(
        UUID dealId,
        String dealReference,
        LocalDate calculationDate,
        BigDecimal pricePerUnit,
        BigDecimal totalAmount,
        BigDecimal annualYtm,
        BigDecimal semiYtm) {
}
