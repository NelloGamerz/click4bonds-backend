package com.click4bonds.app.Modules.Bond.Models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.User.Model.User;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bonds", indexes = {
        @Index(name = "idx_bond_isin", columnList = "isin", unique = true),
        @Index(name = "idx_bond_status", columnList = "status"),
        @Index(name = "idx_bond_maturity_date", columnList = "maturityDate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String isin;

    @Column(nullable = false)
    private String name;

    private String issuer;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal faceValue;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal couponRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponFrequency couponFrequency;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumInvestment;

    @Column(nullable = false)
    private Long totalUnits;

    @Column(nullable = false)
    private Long availableUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BondStatus status = BondStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
