package com.click4bonds.app.Modules.Bond.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial update payload for an issuer.
 *
 * Every field is optional.
 * Only the fields present in the request are modified.
 */
@Data
public class UpdateIssuerRequest {

    // =========================
    // BASIC INFORMATION
    // =========================

    @Size(min = 1, max = 500)
    private String name;

    @Size(max = 255)
    private String shortName;

    @Size(max = 100)
    private String issuerCode;

    @Size(max = 100)
    private String issuerType;

    @Size(max = 150)
    private String sector;

    // =========================
    // REGULATORY IDENTIFICATION
    // =========================

    @Size(max = 30)
    private String cin;

    @Size(max = 20)
    private String pan;

    @Size(max = 30)
    private String lei;

    // =========================
    // ABOUT ISSUER
    // =========================

    private String description;

    @Size(max = 500)
    private String website;

    // =========================
    // ADDRESS
    // =========================

    @Size(max = 1000)
    private String registeredAddress;

    @Size(max = 1000)
    private String corporateAddress;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String pincode;

    // =========================
    // CONTACT INFORMATION
    // =========================

    @Email
    @Size(max = 150)
    private String contactEmail;

    @Size(max = 30)
    private String contactPhone;
}
