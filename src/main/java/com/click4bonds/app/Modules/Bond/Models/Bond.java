package com.click4bonds.app.Modules.Bond.Models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CalculationType;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.User.Model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bonds", indexes = {
        @Index(name = "idx_bond_isin", columnList = "isin", unique = true),
        @Index(name = "idx_bond_status", columnList = "status"),
        @Index(name = "idx_bond_maturity_date", columnList = "maturity_date"),
        @Index(name = "idx_bond_security_type", columnList = "security_type"),
        @Index(name = "idx_bond_rating", columnList = "rating")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // =========================
    // SOURCE / IDENTIFICATION
    // =========================

    /**
     * Serial number from Excel.
     * Can be null because your current sheet does not
     * actually contain a serial number for every bond.
     */
    private Integer serialNumber;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, unique = true, length = 12)
    private String isin;

    // =========================
    // CLASSIFICATION
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SecurityType securityType;

    @Column(length = 100)
    private String rating;

    @Column(length = 200)
    private String ratingAgency;

    /**
     * Category/group from Excel.
     * <p>
     * Examples:
     * High Level SDL below 5 lakh Qtm
     * Category 1 ( Gsec/ SDL )
     * Category 1 (State Gauranteed)
     * Category 2 ( Corporate Bond )
     * Other Than PF Trust
     * Less Than 1 Year Paper
     */
    @Column(length = 255)
    private String category;

    // =========================
    // COUPON
    // =========================

    /**
     * Stored as percentage.
     * <p>
     * Example:
     * 7.20 = 7.20%
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal couponRate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CouponFrequency couponFrequency;

    /**
     * Original IP date text from Excel.
     * <p>
     * Examples:
     * 09/02-09/08
     * 15/10 Ann
     * 31st of every month
     * 23rd of every month
     */
    @Column(length = 255)
    private String ipDateDescription;

    // =========================
    // RECORD DATE
    // =========================

    /**
     * Number of days before each interest payment date on which the record date
     * falls — the entitlement rule for this bond.
     * <p>
     * IMPORTANT - this integer is the SOURCE OF TRUTH for record dates.
     * <p>
     * An admin sends the offset directly, for example {@code 15}. A record date
     * is NOT stored on the bond: it is derived per coupon payment date by
     * {@code RecordDateParser} as {@code paymentDate.minusDays(recordDate)},
     * because a bond with MONTHLY / QUARTERLY / HALF_YEARLY / YEARLY coupons has
     * a different record date for every coupon it pays. Storing a single date
     * here would be wrong for all but one of them.
     * <p>
     * The window from the record date up to (and including) the interest payment
     * date is the <strong>record period</strong>. A settlement falling anywhere
     * inside that window is ex-interest: the coupon belongs to the seller, so
     * the buyer is not entitled to it and the accrued interest on it is not
     * charged ({@code PurchaseConsiderationService}).
     * <p>
     * There is no default offset. {@code NULL} or a non-positive value means the
     * bond has no record-date rule, and those bonds keep their exact historical
     * YTM behaviour: every future coupon is included and accrued interest is
     * always charged.
     */
    @Column(name = "record_date")
    private Integer recordDate;


    // =========================
    // MATURITY
    // =========================

    /**
     * Normalized maturity date.
     * <p>
     * Example:
     * 9/Aug/27 -> 2027-08-09
     */
    private LocalDate maturityDate;

    /**
     * Original maturity text from Excel.
     * <p>
     * Required because maturity can contain
     * amortization/special redemption information.
     * <p>
     * Examples:
     * <p>
     * 26-09-2031
     * 26-09-2031 (2.5% on Each IP till 2027...)
     * 9/11/2024 to 9/11/2033 (10% each year)
     * Perp
     * 01/10/2026 to 01/10/2027 (20% Quartely)
     */
    @Column(length = 1500)
    private String maturityDescription;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MaturityType maturityType;

    // =========================
    // PUT / CALL
    // =========================

    /**
     * We intentionally DO NOT use separate putDate/callDate
     * as the Excel column contains:
     * <p>
     * NA
     * blank
     * date
     * potentially other text
     */
    @Column(length = 255)
    private String putCallDescription;

    // =========================
    // PRICE
    // =========================

    /**
     * Which half of the price/yield pair this bond was created from, and so
     * which half the platform derived.
     *
     * <p>
     * {@code BY_YTM} means the yield was the input and {@link #price} was
     * calculated; {@code BY_PRICE} means the price was the input and
     * {@link #annualYtm} was calculated. Null means the create request declared
     * neither, and both were stored as supplied.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CalculationType calculationType;

    /**
     * Current market/selling price.
     * <p>
     * Can be NULL because Excel may contain blank.
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    // =========================
    // YIELD
    // =========================

    /**
     * IMPORTANT:
     * <p>
     * These are NOT imported from Excel.
     * They are calculated internally.
     * <p>
     * Do not expose them in public APIs unless
     * explicitly required by the organization.
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal semiYtm;

    /**
     * The calculated yield, as a percentage.
     * <p>
     * Four decimal places, because that is what the calculation produces and a
     * basis point is the fourth: two would round away real differences between
     * bonds that quote to a hundredth of a percent. {@code semiYtm} and
     * {@code ytc} stay at two — they are imported, not calculated, and the
     * source states them that way.
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal annualYtm;

    @Column(precision = 10, scale = 4)
    private BigDecimal ytc;

    private Instant ytmCalculatedAt;

    // =========================
    // QUANTUM
    // =========================

    /**
     * Original Excel value.
     * <p>
     * Examples:
     * 3 Lakh
     * 1.50 Lakh
     * 1 Bonds
     * Any
     */
    @Column(length = 100)
    private String quantumDescription;

    /**
     * Normalized value in INR lakhs.
     * <p>
     * 3 Lakh -> 3.00
     * 1.50 Lakh -> 1.50
     * <p>
     * NULL when value is "Any", "1 Bonds", etc.
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal quantumInLacs;

    // =========================
    // LOT SIZE
    // =========================

    /**
     * Original Excel value.
     * <p>
     * Examples:
     * Demat
     * SGL
     * 10 Lakh Lot
     * 775 Lot
     * 1 Crore Lot
     */
    @Column(length = 100)
    private String lotSizeDescription;

    /**
     * Numeric lot size when available.
     * <p>
     * 1000 Lot -> 1000
     * 775 Lot -> 775
     * 10 Lakh Lot -> 1000000
     * 1 Crore Lot -> 10000000
     * <p>
     * NULL for DEMAT / SGL.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal lotSize;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LotSizeType lotSizeType;

    // =========================
    // INVENTORY
    // =========================

    /**
     * Units of this bond still available for purchase.
     * <p>
     * This is the single source of truth for inventory. It is decremented by
     * {@code BondRepository#reserveQuantity}, which is a single conditional
     * UPDATE so two buyers submitting at the same time can never oversell the
     * bond — see {@code DealConfirmationWriter}.
     * <p>
     * NULL means inventory has not been configured for this bond (true of every
     * bond imported before this column existed). Such a bond cannot be
     * purchased; the deal API rejects it with a business error rather than
     * assuming an unlimited supply. A value of {@code 0} is different: it means
     * the bond is sold out.
     * <p>
     * Reaching zero also flips {@link #status} to {@link BondStatus#SOLD_OUT}.
     */
    @Column(name = "remaining_quantity")
    private Long remainingQuantity;

    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BondStatus status = BondStatus.DRAFT;

    @Column(name = "is_flash_news", nullable = false)
    @Builder.Default
    private Boolean isFlashNews = false;

    // =========================
    // BRANDING
    // =========================

    /**
     * Object-store key of this bond's logo, or null when it has none.
     *
     * <p>A key rather than an address, like every other stored object this
     * application keeps. An address carries the endpoint and bucket that built
     * it, so recording one would pin this row to a host that changes and would
     * be meaningless if the bucket were ever fronted by a different domain; a
     * key keeps meaning the same thing until the object is deleted.</p>
     *
     * <p><strong>Mirrored from a document row, and it has one writer.</strong>
     * The logo is stored as a {@link com.click4bonds.app.Modules.Bond.Models.BondDocument}
     * of type {@code LOGO}, and {@code BondDocumentService} is the only thing
     * that sets or clears this field — when such a document is confirmed, and
     * when it is deleted. Nothing else writes it, so the two cannot drift.</p>
     *
     * <p>It is duplicated here rather than read through the document table for
     * one reason: the catalogue shows a logo on every card, and a listing of
     * twenty bonds must not issue twenty more queries to find twenty keys.</p>
     */
    @Column(name = "logo_key", length = 512)
    private String logoKey;

    // =========================
    // AUDIT
    // =========================

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "created_by")
    // private User createdBy;

    // Points at the user's primary key, which is the only identifier a user
    // has. The column used to hold an external provider's identifier instead.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    @Column(length = 255)
    private String sourceFileName;

    private Integer sourceRowNumber;

    private Instant importedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_id")
    private Issuer issuer;
}