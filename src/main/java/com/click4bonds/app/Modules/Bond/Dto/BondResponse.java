package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The bond shape every reader receives — the customer list and detail
 * endpoints and the admin endpoints alike.
 *
 * <p>Deliberately small: a caller gets the six figures a bond is picked on,
 * not the whole stored record. The two derived fields are the reason this is a
 * projection rather than the entity — {@link #rating} joins two stored columns
 * and {@link #minimumInvestment} is arithmetic over two others, so neither is
 * a value anyone has to keep in step.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BondResponse {

    private String name;

    private String isin;

    /**
     * Example:
     * 7.20 means 7.20%
     */
    private BigDecimal couponRate;

    /**
     * Normalized maturity date.
     *
     * Example:
     * 9/Aug/27 -> 2027-08-09
     */
    private LocalDate maturityDate;

    /**
     * Rating agency and grade as one string.
     *
     * Examples:
     * CRISIL AAA
     * ICRA AA+
     */
    private String rating;

    /**
     * What one lot costs, in rupees.
     *
     * <p>Null when the bond has no price, or no numeric lot size to multiply it
     * by — see {@link com.click4bonds.app.Modules.Bond.Service.BondService}.</p>
     */
    private BigDecimal minimumInvestment;
}
