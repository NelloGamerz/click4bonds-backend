package com.click4bonds.app.Modules.Bond.Dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssuerResponse {

    private UUID id;

    private String name;
    private String shortName;
    private String issuerCode;
    private String issuerType;
    private String sector;

    private String cin;
    private String pan;
    private String lei;

    private String description;
    private String website;

    private String registeredAddress;
    private String corporateAddress;

    private String city;
    private String state;
    private String country;
    private String pincode;

    private String contactEmail;
    private String contactPhone;

    private Instant createdAt;
    private Instant updatedAt;
}
