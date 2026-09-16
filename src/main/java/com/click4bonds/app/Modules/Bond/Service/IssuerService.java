package com.click4bonds.app.Modules.Bond.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondIssuerBulkItem;
import com.click4bonds.app.Modules.Bond.Dto.BulkIssuerError;
import com.click4bonds.app.Modules.Bond.Dto.BulkIssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.CreateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.UpdateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Models.Issuer;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Bond.Repository.IssuerRepository;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Issuer operations for a bond.
 *
 * A bond owns at most one issuer, while the same issuer row can be
 * shared by several bonds.
 */
@Service
@RequiredArgsConstructor
public class IssuerService {

    private final IssuerRepository issuerRepository;
    private final BondRepository bondRepository;
    private final IssuerMapper issuerMapper;
    private final IssuerBulkWriter issuerBulkWriter;

    // =========================================================
    // ADD ISSUER TO BOND
    // =========================================================

    @Transactional
    public IssuerResponse addIssuerToBond(
            UUID bondId,
            CreateIssuerRequest request) {

        Bond bond = getBond(bondId);

        if (bond.getIssuer() != null) {
            throw new ConflictException(
                    "Bond already has an issuer: " + bondId
                            + ". Use PATCH to update it.");
        }

        Issuer issuer = issuerMapper.apply(new Issuer(), request);

        issuerMapper.validateUnique(issuer, null);

        Issuer saved = issuerRepository.save(issuer);

        bond.setIssuer(saved);
        bondRepository.save(bond);

        return issuerMapper.toResponse(saved);
    }

    // =========================================================
    // UPDATE ISSUER OF BOND
    // =========================================================

    @Transactional
    public IssuerResponse updateIssuer(
            UUID bondId,
            UpdateIssuerRequest request) {

        Bond bond = getBond(bondId);

        Issuer issuer = bond.getIssuer();

        if (issuer == null) {
            throw new ResourceNotFoundException(
                    "Bond has no issuer yet: " + bondId);
        }

        issuerMapper.applyUpdate(issuer, request);

        issuerMapper.validateUnique(issuer, issuer.getId());

        return issuerMapper.toResponse(
                issuerRepository.save(issuer));
    }

    // =========================================================
    // GET ISSUER OF BOND
    // =========================================================

    @Transactional(readOnly = true)
    public IssuerResponse getIssuerByBondId(UUID bondId) {

        Bond bond = getBond(bondId);

        Issuer issuer = bond.getIssuer();

        if (issuer == null) {
            throw new ResourceNotFoundException(
                    "Bond has no issuer yet: " + bondId);
        }

        return issuerMapper.toResponse(issuer);
    }

    @Transactional(readOnly = true)
    public IssuerResponse getIssuerByIsin(String isin) {

        String normalizedIsin = isin.trim().toUpperCase();

        Bond bond = bondRepository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + normalizedIsin));

        Issuer issuer = bond.getIssuer();

        if (issuer == null) {
            throw new ResourceNotFoundException(
                    "Issuer not found for bond with ISIN: " + normalizedIsin);
        }

        return issuerMapper.toResponse(issuer);
    }

    // =========================================================
    // BULK INSERT / UPSERT
    // =========================================================

    /**
     * Imports issuers for many bonds in one call.
     *
     * Rows are independent: each one is committed on its own, and a
     * failing row is reported in {@code errors} instead of failing
     * the whole request. Rows are keyed by ISIN, so the caller does
     * not need internal bond ids.
     */
    public BulkIssuerResponse bulkUpsertIssuers(
            List<BondIssuerBulkItem> items) {

        List<IssuerResponse> issuers = new ArrayList<>();
        List<BulkIssuerError> errors = new ArrayList<>();

        Set<String> processedIsins = new HashSet<>();
        Set<String> processedCodes = new HashSet<>();
        Set<String> processedCins = new HashSet<>();
        Set<String> processedLeis = new HashSet<>();

        int createdCount = 0;
        int updatedCount = 0;

        for (int index = 0; index < items.size(); index++) {

            int row = index + 1;

            BondIssuerBulkItem item = items.get(index);

            String isin = item.getIsin().trim().toUpperCase();

            String duplicate = findDuplicate(
                    isin,
                    item.getIssuer(),
                    processedIsins,
                    processedCodes,
                    processedCins,
                    processedLeis);

            if (duplicate != null) {
                errors.add(buildError(row, isin, duplicate));
                continue;
            }

            try {

                IssuerBulkWriter.RowOutcome outcome = issuerBulkWriter.writeRow(item);

                issuers.add(outcome.issuer());

                if (outcome.created()) {
                    createdCount++;
                } else {
                    updatedCount++;
                }

                remember(
                        isin,
                        item.getIssuer(),
                        processedIsins,
                        processedCodes,
                        processedCins,
                        processedLeis);

            } catch (RuntimeException ex) {

                errors.add(buildError(row, isin, messageOf(ex)));
            }
        }

        return BulkIssuerResponse.builder()
                .totalRequested(items.size())
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .failedCount(errors.size())
                .issuers(issuers)
                .errors(errors)
                .build();
    }

    // =========================================================
    // BULK HELPERS
    // =========================================================

    /**
     * Rejects the same ISIN twice, and the same issuer_code / CIN /
     * LEI twice, because those columns are unique.
     */
    private String findDuplicate(
            String isin,
            CreateIssuerRequest request,
            Set<String> processedIsins,
            Set<String> processedCodes,
            Set<String> processedCins,
            Set<String> processedLeis) {

        if (processedIsins.contains(isin)) {
            return "Duplicate ISIN in request: " + isin;
        }

        String code = issuerMapper.normalize(request.getIssuerCode());

        if (code != null && processedCodes.contains(code)) {
            return "Duplicate issuer code in request: " + code;
        }

        String cin = issuerMapper.normalize(request.getCin());

        if (cin != null && processedCins.contains(cin)) {
            return "Duplicate CIN in request: " + cin;
        }

        String lei = issuerMapper.normalize(request.getLei());

        if (lei != null && processedLeis.contains(lei)) {
            return "Duplicate LEI in request: " + lei;
        }

        return null;
    }

    private void remember(
            String isin,
            CreateIssuerRequest request,
            Set<String> processedIsins,
            Set<String> processedCodes,
            Set<String> processedCins,
            Set<String> processedLeis) {

        processedIsins.add(isin);

        String code = issuerMapper.normalize(request.getIssuerCode());

        if (code != null) {
            processedCodes.add(code);
        }

        String cin = issuerMapper.normalize(request.getCin());

        if (cin != null) {
            processedCins.add(cin);
        }

        String lei = issuerMapper.normalize(request.getLei());

        if (lei != null) {
            processedLeis.add(lei);
        }
    }

    private BulkIssuerError buildError(
            int row,
            String isin,
            String message) {

        return BulkIssuerError.builder()
                .row(row)
                .isin(isin)
                .message(message)
                .build();
    }

    private String messageOf(RuntimeException ex) {

        String message = ex.getMessage();

        return (message == null || message.isBlank())
                ? ex.getClass().getSimpleName()
                : message;
    }

    // =========================================================
    // INTERNAL ENTITY LOOKUP
    // =========================================================

    private Bond getBond(UUID id) {

        return bondRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found: " + id));
    }
}
