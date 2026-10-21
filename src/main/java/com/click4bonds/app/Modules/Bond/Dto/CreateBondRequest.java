package com.click4bonds.app.Modules.Bond.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin create-bond request.
 *
 * <p>
 * Every value is accepted in the form the source sheet holds it — {@code "8.45%"},
 * {@code "Secured"}, {@code "7/Mar/28"}, {@code "1.50 Lakh"}, {@code "10 Lacs
 * Lot"} — and normalized before it reaches the entity by
 * {@code BondRequestMapper} and {@code BondFieldParser}. A value that cannot be
 * parsed fails the whole request with {@code 400}, naming the field; nothing
 * half-parsed is written.
 *
 * <p>
 * The normalized companions ({@code quantum}, {@code lotSize},
 * {@code lotSizeType}, {@code maturityDate}, {@code maturityType},
 * {@code couponFrequency}) may still be sent explicitly, and then they win over
 * what the raw text would have produced.
 */
@Data
public class CreateBondRequest {

    // =========================
    // IDENTIFICATION
    // =========================

    /**
     * Can be null because the Excel sheet may not provide
     * a serial number for every bond.
     */
    private Integer serialNumber;

    @NotBlank
    @Size(max = 500)
    private String name;

    @NotBlank
    @Size(max = 12)
    private String isin;

    // =========================
    // CLASSIFICATION
    // =========================

    /**
     * Example:
     * Category 1 ( Gsec/ SDL )
     * Category 2 ( Corporate Bond )
     */
    @Size(max = 255)
    private String category;

    /**
     * Raw text.
     *
     * Examples:
     * Secured
     * Unsecured
     */
    @NotBlank
    private String securityType;

    /**
     * Examples:
     * Sovereign
     * BBB- (CE)
     * AA+
     */
    @Size(max = 100)
    private String rating;

    /**
     * Examples:
     * ICRA & CARE
     * CRISIL & ICRA
     */
    @Size(max = 200)
    private String ratingAgency;

    // =========================
    // COUPON
    // =========================

    /**
     * Raw percentage.
     *
     * Examples:
     * 8.45%
     * 0.40%
     */
    @NotBlank
    private String couponRate;

    /**
     * Raw frequency. Optional — inferred from {@link #ipDateDescription} when
     * absent.
     *
     * Examples:
     * Ann
     * Quarterly
     * 1st of Every Month
     */
    private String couponFrequency;

    /**
     * Examples:
     *
     * 07/03-07/09
     * 15/10 Ann
     * 1st of Every Month
     */
    @Size(max = 255)
    private String ipDateDescription;

    // =========================
    // RECORD DATE
    // =========================

    /**
     * Number of days before each interest payment date on which the record date
     * falls.
     *
     * <p>
     * This is the source of truth for entitlement. The record date itself is
     * derived per coupon payment date as {@code paymentDate - recordDate} and is
     * never supplied by a client.
     *
     * Examples:
     *
     * 15
     * 2
     *
     * <p>
     * Optional. Omitted or {@code null} means the bond has no record-date rule.
     */
    @Min(1)
    private Integer recordDate;

    // =========================
    // MATURITY
    // =========================

    /**
     * Raw maturity type. Optional — derived from {@link #maturityDescription}
     * when absent.
     */
    private String maturityType;

    /**
     * Raw maturity date. Optional — derived from
     * {@link #maturityDescription} when absent.
     *
     * Examples:
     * 7/Mar/28
     * 15/Oct/33
     */
    private String maturityDate;

    /**
     * Raw maturity text. Carries amortization/redemption detail.
     *
     * Examples:
     *
     * 26-09-2031 (2.5% on Each IP till 2027...)
     *
     * 9/11/2024 to 9/11/2033 (10% each year)
     *
     * Perp
     */
    @Size(max = 1500)
    private String maturityDescription;

    // =========================
    // PUT / CALL
    // =========================

    /**
     * Examples:
     *
     * NA
     * 31/Jan/33
     * blank
     */
    @Size(max = 255)
    private String putCallDescription;

    // =========================
    // PRICE
    // =========================

    /**
     * Price can be blank in Excel,
     * therefore this is optional.
     *
     * Examples:
     * 102.08
     */
    private String price;

    // =========================
    // YIELD
    // =========================

    /**
     * Yield supplied by the source sheet. Stored as-is; the calculation engine
     * overwrites it the next time a yield is derived from the price.
     *
     * Examples:
     * 6.88%
     * 7.00%
     */
    private String semiYtm;

    private String annualYtm;

    private String ytc;

    // =========================
    // CALCULATION
    // =========================

    /**
     * Which of {@link #price} and {@link #annualYtm} the request is supplying, and
     * so which one the platform works out:
     *
     * <pre>
     * BY_PRICE : price supplied -> the yield is calculated from it
     * BY_YTM   : yield supplied -> the price is calculated from it
     * </pre>
     *
     * <p>
     * The derived figure overwrites whatever the request sent for the other half,
     * so the two can never disagree. Optional: when absent, neither is derived and
     * both are stored exactly as sent, which is the behaviour existing clients
     * rely on.
     *
     * <p>
     * Read case-insensitively; an unrecognized value fails the request with
     * {@code 400}, naming the field.
     *
     * Examples:
     * BY_PRICE
     * BY_YTM
     */
    private String calculationType;

    // =========================
    // QUANTUM
    // =========================

    /**
     * Original value from the sheet.
     *
     * Examples:
     * 3 Lakh
     * 1.50 Lakh
     * Any
     * 1 Bonds
     */
    @Size(max = 100)
    private String quantumDescription;

    /**
     * Optional explicit normalized value in lakhs. When absent it is derived
     * from {@link #quantumDescription}.
     *
     * <p>The request speaks in lakhs, but the bond stores the full INR amount:
     * {@code 3.00} lakh is stored as {@code 300000.00}.</p>
     *
     * Example:
     * 3 Lakh -> 3.00
     */
    private String quantum;

    // =========================
    // LOT SIZE
    // =========================

    /**
     * Original sheet value.
     *
     * Examples:
     * Demat
     * SGL
     * 1000 Lot
     * 10 Lacs Lot
     * 1 Crore Lot
     */
    @Size(max = 100)
    private String lotSizeDescription;

    /**
     * Optional explicit normalized lot size. When absent it is derived from
     * {@link #lotSizeDescription}.
     *
     * Example:
     * 10 Lacs Lot -> 1000000
     */
    private String lotSize;

    /**
     * Optional explicit lot type. When absent it is derived from
     * {@link #lotSizeDescription}.
     */
    private String lotSizeType;

    // =========================
    // FLAGS
    // =========================

    /**
     * Whether the bond is shown as flash news.
     *
     * <p>Optional; omitting it stores {@code false}.</p>
     */
    private Boolean isFlashNews;
}
