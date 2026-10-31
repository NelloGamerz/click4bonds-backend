package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;

/**
 * The figures a deal is struck at, when they were negotiated before the deal
 * existed.
 *
 * <p>Passed to the writer so a back-office deal can be saved with the price and
 * yield an employee previewed, instead of the bond's own quoted price. Null on
 * the create path means "no negotiated figures": the bond's price is used and no
 * deal-scoped yield is stored, exactly as before.</p>
 */
public record DealPricing(
        BigDecimal pricePerUnit,
        BigDecimal annualYtm,
        BigDecimal semiYtm) {
}
