package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CalculationType;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * A single bond in full: every field the platform stores about it, plus the
 * normalized values parsed from the raw source text.
 *
 * <p>This is what one bond's own page is entitled to. {@link BondResponse} is the
 * summary a list is built from — cheap enough to repeat per row and holding only
 * the figures a card shows — and this is the record behind it. The two are
 * deliberately separate types rather than one shape with most fields null,
 * because a list of twenty bonds should not carry twenty times the detail a
 * caller can only be looking at one of.</p>
 *
 * <p>Fields grouped as the entity groups them: identification, classification,
 * coupon, record date, maturity, put/call, price, yield, quantum, lot size,
 * inventory, status, audit. Raw text columns are paired with their normalized
 * forms — {@code ipDateDescription} beside the schedule it parses to,
 * {@code lotSizeDescription} beside {@code lotSize} — so a caller can show what
 * was entered and use what it means.</p>
 *
 * <p>Everything the entity holds that is <em>about the bond</em>: the columns
 * recording which import the row arrived in, and the relationships to the
 * creating user and the issuer, are deliberately not here. The import columns
 * are internal provenance a customer has no use for, and the two relationships
 * are separate reads. {@link AdminBondDetailResponse} extends this with the
 * provenance, which is what an admin gets.</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BondDetailResponse {

    private UUID id;

    // =========================
    // IDENTIFICATION
    // =========================

    private Integer serialNumber;

    private String name;

    private String isin;

    // =========================
    // CLASSIFICATION
    // =========================

    private String category;

    private SecurityType securityType;

    private String rating;

    private String ratingAgency;

    // =========================
    // COUPON
    // =========================

    /**
     * Example:
     * 7.20 means 7.20%
     */
    private BigDecimal couponRate;

    private CouponFrequency couponFrequency;

    /**
     * Examples:
     * 09/02-09/08
     * 15/10 Ann
     * 31st of every month
     */
    private String ipDateDescription;

    // =========================
    // RECORD DATE
    // =========================

    /**
     * Number of days before each interest payment date on which the record date
     * falls; {@code null} when the bond has no record-date rule.
     *
     * <p>
     * Record dates themselves are derived from this offset per coupon payment
     * date and are not returned as a single value, because a bond with recurring
     * coupons has a different record date for every payment.
     */
    private Integer recordDate;

    // =========================
    // MATURITY
    // =========================

    private MaturityType maturityType;

    /**
     * Normalized maturity date.
     *
     * Example:
     * 9/Aug/27 -> 2027-08-09
     */
    private LocalDate maturityDate;

    /**
     * Original maturity information from Excel.
     *
     * Examples:
     *
     * "26-09-2031 (2.5% on Each IP till 2027...)"
     *
     * "9/11/2024 to 9/11/2033 (10% each year)"
     *
     * "Perp"
     */
    private String maturityDescription;

    // =========================
    // PUT / CALL
    // =========================

    /**
     * Examples:
     *
     * NA
     * 31/Jan/33
     * null
     */
    private String putCallDescription;

    // =========================
    // PRICE
    // =========================

    /**
     * Which of {@link #price} and {@link #annualYtm} was the input when this bond
     * was created, and so which one the platform derived. Null when the create
     * request declared neither, in which case both are as supplied.
     */
    private CalculationType calculationType;

    /**
     * Can be null when price is not available.
     */
    private BigDecimal price;

    // =========================
    // YIELD
    // =========================

    /**
     * Yields, stored as percentages — 6.88 means 6.88%.
     *
     * <p>Supplied by an admin on create/update, or calculated by the engine
     * from the price. {@code ytmCalculatedAt} records when the value was last
     * set, so a reader can tell how fresh it is.</p>
     */
    private BigDecimal semiYtm;

    private BigDecimal annualYtm;

    private BigDecimal ytc;

    private Instant ytmCalculatedAt;

    // =========================
    // QUANTUM
    // =========================

    /**
     * Original Excel value.
     *
     * Examples:
     * 3 Lakh
     * 1.50 Lakh
     * Any
     * 1 Bonds
     */
    private String quantumDescription;

    /**
     * Normalized value in lakhs.
     */
    private BigDecimal quantumInLacs;

    // =========================
    // LOT SIZE
    // =========================

    /**
     * Original Excel value.
     *
     * Examples:
     * Demat
     * SGL
     * 1000 Lot
     * 10 Lakh Lot
     * 1 Crore Lot
     */
    private String lotSizeDescription;

    /**
     * Normalized numeric lot size.
     *
     * NULL for DEMAT / SGL.
     */
    private BigDecimal lotSize;

    private LotSizeType lotSizeType;

    private Boolean isFlashNews;

    // =========================
    // INVENTORY
    // =========================

    /**
     * Units still available for purchase.
     *
     * <p>NULL means inventory has not been configured for this bond, so it
     * cannot be purchased yet.</p>
     */
    private Long remainingQuantity;

    // =========================
    // STATUS
    // =========================

    private BondStatus status;

    // =========================
    // AUDIT
    // =========================

    private Instant createdAt;

    private Instant updatedAt;
}
