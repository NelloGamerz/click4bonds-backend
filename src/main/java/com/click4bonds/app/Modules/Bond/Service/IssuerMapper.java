package com.click4bonds.app.Modules.Bond.Service;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Bond.Dto.CreateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.UpdateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Models.Issuer;

import lombok.RequiredArgsConstructor;

/**
 * Mapping for {@link Issuer}.
 *
 * Shared by {@link IssuerService} (single bond) and
 * {@link IssuerBulkWriter} (bulk import). Uniqueness is decided by
 * {@link IssuerService#validateUnique}, since that is the class that owns the
 * issuer store.
 */
@Component
@RequiredArgsConstructor
public class IssuerMapper {

    // =========================================================
    // CREATE / BULK UPSERT
    // =========================================================

    /**
     * Copies the fields present in the request onto the issuer.
     *
     * Absent fields are left untouched, so a bulk re-import that
     * omits a column does not wipe the stored value. For a new
     * issuer this is simply a full insert.
     */
    public Issuer apply(Issuer target, CreateIssuerRequest request) {

        if (request.getName() != null) {
            target.setName(normalize(request.getName()));
        }

        if (request.getShortName() != null) {
            target.setShortName(normalize(request.getShortName()));
        }

        if (request.getIssuerCode() != null) {
            target.setIssuerCode(normalize(request.getIssuerCode()));
        }

        if (request.getIssuerType() != null) {
            target.setIssuerType(normalize(request.getIssuerType()));
        }

        if (request.getSector() != null) {
            target.setSector(normalize(request.getSector()));
        }

        if (request.getCin() != null) {
            target.setCin(normalize(request.getCin()));
        }

        if (request.getPan() != null) {
            target.setPan(normalize(request.getPan()));
        }

        if (request.getLei() != null) {
            target.setLei(normalize(request.getLei()));
        }

        if (request.getDescription() != null) {
            target.setDescription(normalize(request.getDescription()));
        }

        if (request.getWebsite() != null) {
            target.setWebsite(normalize(request.getWebsite()));
        }

        if (request.getRegisteredAddress() != null) {
            target.setRegisteredAddress(normalize(request.getRegisteredAddress()));
        }

        if (request.getCorporateAddress() != null) {
            target.setCorporateAddress(normalize(request.getCorporateAddress()));
        }

        if (request.getCity() != null) {
            target.setCity(normalize(request.getCity()));
        }

        if (request.getState() != null) {
            target.setState(normalize(request.getState()));
        }

        if (request.getCountry() != null) {
            target.setCountry(normalize(request.getCountry()));
        }

        if (request.getPincode() != null) {
            target.setPincode(normalize(request.getPincode()));
        }

        if (request.getContactEmail() != null) {
            target.setContactEmail(normalize(request.getContactEmail()));
        }

        if (request.getContactPhone() != null) {
            target.setContactPhone(normalize(request.getContactPhone()));
        }

        return target;
    }

    // =========================================================
    // PARTIAL UPDATE
    // =========================================================

    /**
     * Applies only the fields present in the request.
     */
    public void applyUpdate(Issuer issuer, UpdateIssuerRequest request) {

        if (request.getName() != null) {
            issuer.setName(normalize(request.getName()));
        }

        if (request.getShortName() != null) {
            issuer.setShortName(normalize(request.getShortName()));
        }

        if (request.getIssuerCode() != null) {
            issuer.setIssuerCode(normalize(request.getIssuerCode()));
        }

        if (request.getIssuerType() != null) {
            issuer.setIssuerType(normalize(request.getIssuerType()));
        }

        if (request.getSector() != null) {
            issuer.setSector(normalize(request.getSector()));
        }

        if (request.getCin() != null) {
            issuer.setCin(normalize(request.getCin()));
        }

        if (request.getPan() != null) {
            issuer.setPan(normalize(request.getPan()));
        }

        if (request.getLei() != null) {
            issuer.setLei(normalize(request.getLei()));
        }

        if (request.getDescription() != null) {
            issuer.setDescription(normalize(request.getDescription()));
        }

        if (request.getWebsite() != null) {
            issuer.setWebsite(normalize(request.getWebsite()));
        }

        if (request.getRegisteredAddress() != null) {
            issuer.setRegisteredAddress(normalize(request.getRegisteredAddress()));
        }

        if (request.getCorporateAddress() != null) {
            issuer.setCorporateAddress(normalize(request.getCorporateAddress()));
        }

        if (request.getCity() != null) {
            issuer.setCity(normalize(request.getCity()));
        }

        if (request.getState() != null) {
            issuer.setState(normalize(request.getState()));
        }

        if (request.getCountry() != null) {
            issuer.setCountry(normalize(request.getCountry()));
        }

        if (request.getPincode() != null) {
            issuer.setPincode(normalize(request.getPincode()));
        }

        if (request.getContactEmail() != null) {
            issuer.setContactEmail(normalize(request.getContactEmail()));
        }

        if (request.getContactPhone() != null) {
            issuer.setContactPhone(normalize(request.getContactPhone()));
        }
    }

    // =========================================================
    // NORMALIZATION
    // =========================================================

    /**
     * Blank strings are stored as NULL.
     *
     * Without this, two issuers with an empty issuer_code would
     * both try to insert "" into a unique column and fail.
     */
    public String normalize(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    // =========================================================
    // ENTITY -> RESPONSE
    // =========================================================

    public IssuerResponse toResponse(Issuer issuer) {

        return IssuerResponse.builder()

                .id(issuer.getId())

                // Basic information
                .name(issuer.getName())
                .shortName(issuer.getShortName())
                .issuerCode(issuer.getIssuerCode())
                .issuerType(issuer.getIssuerType())
                .sector(issuer.getSector())

                // Regulatory identification
                .cin(issuer.getCin())
                .pan(issuer.getPan())
                .lei(issuer.getLei())

                // About issuer
                .description(issuer.getDescription())
                .website(issuer.getWebsite())

                // Address
                .registeredAddress(issuer.getRegisteredAddress())
                .corporateAddress(issuer.getCorporateAddress())
                .city(issuer.getCity())
                .state(issuer.getState())
                .country(issuer.getCountry())
                .pincode(issuer.getPincode())

                // Contact
                .contactEmail(issuer.getContactEmail())
                .contactPhone(issuer.getContactPhone())

                // Audit
                .createdAt(issuer.getCreatedAt())
                .updatedAt(issuer.getUpdatedAt())

                .build();
    }
}
