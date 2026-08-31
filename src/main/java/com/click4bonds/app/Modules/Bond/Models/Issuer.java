package com.click4bonds.app.Modules.Bond.Models;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issuers", indexes = {
        @Index(name = "idx_issuer_name", columnList = "name"),
        @Index(name = "idx_issuer_code", columnList = "issuer_code", unique = true),
        @Index(name = "idx_issuer_cin", columnList = "cin", unique = true),
        @Index(name = "idx_issuer_lei", columnList = "lei", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issuer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // =========================
    // BASIC INFORMATION
    // =========================

    /**
     * Full legal/display name of the issuer.
     *
     * Examples:
     * Bank of India
     * HDFC Bank Limited
     * Tata Capital Financial Services Limited
     */
    @Column(nullable = false, length = 500)
    private String name;

    /**
     * Short/display name of the issuer.
     *
     * Example:
     * HDFC Bank
     */
    @Column(length = 255)
    private String shortName;

    /**
     * Internal Click4Bonds issuer code.
     *
     * Example:
     * BOI
     * HDFCBANK
     * TCFSL
     */
    @Column(name = "issuer_code", unique = true, length = 100)
    private String issuerCode;

    /**
     * Type/category of issuer.
     *
     * Examples:
     * BANK
     * NBFC
     * CORPORATE
     * PSU
     * GOVERNMENT
     * STATE_GOVERNMENT
     */
    @Column(length = 100)
    private String issuerType;

    /**
     * Industry/business sector.
     *
     * Examples:
     * Banking
     * Financial Services
     * Infrastructure
     * Manufacturing
     */
    @Column(length = 150)
    private String sector;

    // =========================
    // REGULATORY IDENTIFICATION
    // =========================

    /**
     * Corporate Identification Number.
     */
    @Column(length = 30, unique = true)
    private String cin;

    /**
     * Permanent Account Number.
     */
    @Column(length = 20)
    private String pan;

    /**
     * Legal Entity Identifier.
     */
    @Column(length = 30, unique = true)
    private String lei;

    // =========================
    // ABOUT ISSUER
    // =========================

    /**
     * Description shown in "About Issuer".
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Official issuer/company website.
     */
    @Column(length = 500)
    private String website;

    // =========================
    // ADDRESS
    // =========================

    /**
     * Registered office address.
     */
    @Column(length = 1000)
    private String registeredAddress;

    /**
     * Corporate office address.
     */
    @Column(length = 1000)
    private String corporateAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    @Column(length = 20)
    private String pincode;

    // =========================
    // CONTACT INFORMATION
    // =========================

    @Column(length = 150)
    private String contactEmail;

    @Column(length = 30)
    private String contactPhone;

    // =========================
    // AUDIT
    // =========================

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
