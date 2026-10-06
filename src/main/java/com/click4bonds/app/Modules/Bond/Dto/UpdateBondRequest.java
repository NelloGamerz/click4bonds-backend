package com.click4bonds.app.Modules.Bond.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin partial-update request.
 *
 * <p>
 * A null field means "leave untouched". Every non-null value is accepted in the
 * raw form the source sheet holds it and normalized by
 * {@code BondRequestMapper}; an unparseable value fails the request with
 * {@code 400} naming the field.
 *
 * <p>
 * The normalized companions ({@code quantumInLacs}, {@code lotSize},
 * {@code lotSizeType}, {@code maturityDate}, {@code maturityType},
 * {@code couponFrequency}) may also be sent explicitly, and then they win over
 * what the raw text would have produced.
 */
@Data
public class UpdateBondRequest {

    // =========================
    // IDENTIFICATION
    // =========================

    private Integer serialNumber;

    @Size(min = 1, max = 500)
    private String name;

    // =========================
    // CLASSIFICATION
    // =========================

    @Size(max = 255)
    private String category;

    /**
     * Raw text, e.g. {@code Secured}.
     */
    private String securityType;

    @Size(max = 100)
    private String rating;

    @Size(max = 200)
    private String ratingAgency;

    // =========================
    // COUPON
    // =========================

    /**
     * Raw percentage, e.g. {@code 8.45%}.
     */
    private String couponRate;

    /**
     * Raw frequency, e.g. {@code Ann} or {@code Quarterly}.
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
     * The record-date rule as supplied by the source.
     *
     * Examples:
     *
     * 15 days prior to interest payment date
     * 2 days before coupon
     * NA
     */
    @Size(max = 255)
    private String recordDateDescription;

    // =========================
    // MATURITY
    // =========================

    private String maturityType;

    private String maturityDate;

    /**
     * Raw maturity text.
     *
     * Examples:
     *
     * 9/Aug/27
     * Perp
     * 26-09-2031 (2.5% on Each IP till 2027...)
     * 9/11/2024 to 9/11/2033 (10% each year)
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
     * Raw price, e.g. {@code 102.08}. Blank in the sheet means null here.
     */
    private String price;

    // =========================
    // YIELD
    // =========================

    /**
     * Yield supplied by the source sheet. When sent, it is stored and the
     * previously calculated yield is not left behind.
     *
     * Examples:
     * 6.88%
     * 7.00%
     */
    private String semiYtm;

    private String annualYtm;

    private String ytc;

    // =========================
    // QUANTUM
    // =========================

    /**
     * Original sheet value.
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
     * Optional explicit normalized value in lakhs.
     */
    private String quantumInLacs;

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
     * Optional explicit normalized lot size.
     */
    private String lotSize;

    /**
     * Optional explicit lot type.
     */
    private String lotSizeType;

    // =========================
    // INVENTORY
    // =========================

    /**
     * Units available for purchase.
     *
     * <p>Optional. When supplied it REPLACES the current inventory — it is not a
     * delta — so an admin restocking a bond sends the new total. Because every
     * other field on this request is null-tolerant, a null here leaves the
     * current inventory untouched.</p>
     */
    @Min(0)
    private Long remainingQuantity;

    private Boolean isFlashNews;
}
