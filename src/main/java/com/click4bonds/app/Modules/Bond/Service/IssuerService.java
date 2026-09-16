package com.click4bonds.app.Modules.Bond.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondIssuerBulkItem;
import com.click4bonds.app.Modules.Bond.Dto.BulkIssuerError;
import com.click4bonds.app.Modules.Bond.Dto.BulkIssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.CreateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Dto.IssuerPageResponse;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.UpdateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Models.Issuer;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Bond.Repository.IssuerRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
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

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Separates the name from the id inside a cursor. A name may
     * contain it, so decoding splits on the last occurrence.
     */
    private static final char CURSOR_SEPARATOR = '|';

    // =========================================================
    // ADD ISSUER TO BOND
    // =========================================================

    @Transactional
    public IssuerResponse addIssuerToBond(
            String isin,
            CreateIssuerRequest request) {

        Bond bond = getBondByIsin(isin);

        if (bond.getIssuer() != null) {
            throw new ConflictException(
                    "Bond already has an issuer: " + isin
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
            String isin,
            UpdateIssuerRequest request) {

        Bond bond = getBondByIsin(isin);

        Issuer issuer = bond.getIssuer();

        if (issuer == null) {
            throw new ResourceNotFoundException(
                    "Bond has no issuer yet: " + isin);
        }

        issuerMapper.applyUpdate(issuer, request);

        issuerMapper.validateUnique(issuer, issuer.getId());

        return issuerMapper.toResponse(
                issuerRepository.save(issuer));
    }

    // =========================================================
    // GET ISSUER OF BOND
    // =========================================================

    /**
     * The issuer attached to the bond with the given ISIN.
     */
    @Transactional(readOnly = true)
    public IssuerResponse getIssuerByIsin(String isin) {

        Bond bond = getBondByIsin(isin);

        Issuer issuer = bond.getIssuer();

        if (issuer == null) {
            throw new ResourceNotFoundException(
                    "Issuer not found for bond with ISIN: " + bond.getIsin());
        }

        return issuerMapper.toResponse(issuer);
    }

    // =========================================================
    // LIST ISSUERS (CURSOR PAGINATION)
    // =========================================================

    /**
     * Lists issuers ordered by name, optionally filtered by a
     * case-insensitive substring of the name.
     *
     * @param search optional name fragment. NULL or blank returns
     *               every issuer.
     * @param cursor opaque cursor from the previous page's
     *               {@code nextCursor}. NULL for the first page.
     * @param size   page size. Clamped to [1, 100], default 20.
     */
    @Transactional(readOnly = true)
    public IssuerPageResponse getIssuers(
            String search,
            String cursor,
            Integer size) {

        int pageSize = resolvePageSize(size);

        String pattern = buildSearchPattern(search);

        // One extra row tells us whether another page exists,
        // without a second count query.
        Pageable limit = PageRequest.of(0, pageSize + 1);

        List<Issuer> rows;

        if (cursor == null || cursor.isBlank()) {

            rows = issuerRepository.findPageBySearch(
                    pattern,
                    limit);

        } else {

            Cursor decoded = decodeCursor(cursor);

            rows = issuerRepository.findPageBySearchAfter(
                    pattern,
                    decoded.name(),
                    decoded.id(),
                    limit);
        }

        boolean hasNext = rows.size() > pageSize;

        List<Issuer> page = hasNext
                ? rows.subList(0, pageSize)
                : rows;

        return IssuerPageResponse.builder()
                .items(
                        page.stream()
                                .map(issuerMapper::toResponse)
                                .toList())
                .size(page.size())
                .hasNext(hasNext)
                .nextCursor(hasNext
                        ? encodeCursor(page.get(page.size() - 1))
                        : null)
                .build();
    }

    /**
     * The (name, id) of the last row of a page.
     *
     * {@code name} is stored lower-cased because the query orders
     * and compares on {@code LOWER(name)}.
     */
    private record Cursor(String name, UUID id) {
    }

    private int resolvePageSize(Integer size) {

        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Builds the LIKE pattern for the name search.
     *
     * An empty search becomes "%", matching every name. %, _ and
     * the escape character itself are escaped so that a search for
     * e.g. "50%" looks for a literal percent sign.
     */
    private String buildSearchPattern(String search) {

        if (search == null || search.isBlank()) {
            return "%";
        }

        String escaped = search.trim()
                .toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escaped + "%";
    }

    private String encodeCursor(Issuer issuer) {

        String raw = issuer.getName().toLowerCase()
                + CURSOR_SEPARATOR
                + issuer.getId();

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {

        String raw;

        try {

            raw = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);

        } catch (IllegalArgumentException ex) {

            throw new BadRequestException("Invalid cursor");
        }

        int separator = raw.lastIndexOf(CURSOR_SEPARATOR);

        if (separator < 0) {
            throw new BadRequestException("Invalid cursor");
        }

        try {

            return new Cursor(
                    raw.substring(0, separator),
                    UUID.fromString(raw.substring(separator + 1)));

        } catch (IllegalArgumentException ex) {

            throw new BadRequestException("Invalid cursor");
        }
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

    /**
     * Looks a bond up by its ISIN.
     *
     * ISINs are stored upper-cased, so the input is normalized
     * first — that way an ISIN typed in lower case in a path
     * variable still resolves.
     */
    private Bond getBondByIsin(String isin) {

        String normalizedIsin = isin == null
                ? null
                : isin.trim().toUpperCase();

        return bondRepository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + normalizedIsin));
    }
}
