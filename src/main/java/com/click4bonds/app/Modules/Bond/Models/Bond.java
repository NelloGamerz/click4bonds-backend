package com.click4bonds.app.Modules.Bond.Models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.User.Model.User;

import jakarta.persistence.*;
import lombok.*;

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
     *
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
     *
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
     *
     * Examples:
     * 09/02-09/08
     * 15/10 Ann
     * 31st of every month
     * 23rd of every month
     */
    @Column(length = 255)
    private String ipDateDescription;

    // =========================
    // MATURITY
    // =========================

    /**
     * Normalized maturity date.
     *
     * Example:
     * 9/Aug/27 -> 2027-08-09
     */
    private LocalDate maturityDate;

    /**
     * Original maturity text from Excel.
     *
     * Required because maturity can contain
     * amortization/special redemption information.
     *
     * Examples:
     *
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
     *
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
     * Current market/selling price.
     *
     * Can be NULL because Excel may contain blank.
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    // =========================
    // YIELD
    // =========================

    /**
     * IMPORTANT:
     *
     * These are NOT imported from Excel.
     * They are calculated internally.
     *
     * Do not expose them in public APIs unless
     * explicitly required by the organization.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal semiYtm;

    @Column(precision = 10, scale = 2)
    private BigDecimal annualYtm;

    @Column(precision = 10, scale = 2)
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
     * 1 Bonds
     * Any
     */
    @Column(length = 100)
    private String quantumDescription;

    /**
     * Normalized value in INR lakhs.
     *
     * 3 Lakh -> 3.00
     * 1.50 Lakh -> 1.50
     *
     * NULL when value is "Any", "1 Bonds", etc.
     */
    @Column(precision = 19, scale = 2)
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
     * 10 Lakh Lot
     * 775 Lot
     * 1 Crore Lot
     */
    @Column(length = 100)
    private String lotSizeDescription;

    /**
     * Numeric lot size when available.
     *
     * 1000 Lot -> 1000
     * 775 Lot -> 775
     * 10 Lakh Lot -> 1000000
     * 1 Crore Lot -> 10000000
     *
     * NULL for DEMAT / SGL.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal lotSize;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LotSizeType lotSizeType;

    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BondStatus status = BondStatus.DRAFT;

    // =========================
    // AUDIT
    // =========================

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "created_by")
    // private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "clerk_user_id")
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