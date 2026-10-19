package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The bond shape every reader receives — the customer list and detail
 * endpoints and the admin endpoints alike.
 *
 * <p>Deliberately small: a caller gets the handful of figures a bond is picked
 * on, not the whole stored record. The two derived fields are the reason this is
 * a projection rather than the entity — {@link #rating} joins two stored columns
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

    private CouponFrequency couponFrequency;

    /**
     * Annual yield to maturity, as a percentage to 4 decimals — 8.6521 means
     * 8.6521%.
     *
     * <p>Calculated by the platform rather than imported, and derived from the
     * price when a bond is created or updated. Null until it has been
     * calculated, which a bond imported with no price and no yield never is.</p>
     */
    private BigDecimal annualYtm;

    private MaturityType maturityType;

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

    /**
     * Where to fetch the bond's logo, or null when the bond has no logo.
     *
     * <p>Absolute when a public base URL is configured for the bucket — e.g.
     * {@code https://assets.indiabonds.com/bonds/ine123a01011/logo/...png} —
     * and otherwise relative to the API root, e.g.
     * {@code /api/bonds/INE123A01011/logo}. See
     * {@link com.click4bonds.app.Modules.Storage.Config.StorageProperties.R2#getPublicBaseUrl()}.</p>
     *
     * <p>Neither form exposes the private bucket directly: the absolute one
     * only works for paths an operator has published on the custom domain, and
     * the relative one redirects to a freshly signed URL. A signed URL itself
     * would be the wrong thing to hand out here, because it expires within
     * minutes and a catalogue card may be on screen for longer.</p>
     *
     * <p>It is safe to put straight into an {@code <img>} element: both forms
     * are public, because a catalogue is, and an image element cannot send an
     * {@code Authorization} header.</p>
     */
    private String logoUrl;
}
