package com.click4bonds.app.Modules.Admin.Dto;

import java.math.BigDecimal;

/**
 * A request to price one deal, or to solve the yield it was priced at.
 *
 * <p>Exactly one of the two is supplied; the service refuses a request that
 * supplies neither or both. The figure given is the input and the other is
 * calculated, then both are saved on the deal.</p>
 *
 * @param price     clean price per unit as a percentage of par, e.g. {@code 98.94}
 * @param annualYtm annual yield as a percentage, e.g. {@code 10.6947}
 */
public record CalculateDealPricingRequest(
        BigDecimal price,
        BigDecimal annualYtm) {
}
