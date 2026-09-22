package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point for buying a bond.
 *
 * <p>Wraps the transactional {@link DealConfirmationWriter} with the two things
 * that must happen outside its transaction: recognising a retried request, and
 * generating the confirmation document.</p>
 *
 * <p>This class is deliberately not transactional. The document step is an
 * external operation that will eventually fill a spreadsheet and render a PDF,
 * and holding a database transaction open across it would pin a connection for
 * the duration.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationService {

    private final DealConfirmationWriter writer;
    private final DealConfirmationRepository dealConfirmationRepository;
    private final DealConfirmationMapper mapper;
    private final DealConfirmationDocumentService documentService;

    /**
     * Outcome of a create request.
     *
     * @param response what the customer gets back
     * @param replayed true when this request was a duplicate that was answered
     *                 with the deal created by the first one, and therefore
     *                 bought nothing
     */
    public record Result(DealConfirmationResponse response, boolean replayed) {
    }

    /**
     * Creates a deal, or replays the one an identical earlier request created.
     *
     * @param clerkUserId    authenticated customer, from the JWT
     * @param request        bond and quantities
     * @param idempotencyKey client-supplied key, or null when the client sent none
     */
    public Result createDeal(
            String clerkUserId,
            CreateDealConfirmationRequest request,
            String idempotencyKey) {

        String key = normalizeKey(idempotencyKey);

        if (key != null) {

            DealConfirmation existing = findByIdempotencyKey(clerkUserId, key);

            if (existing != null) {

                log.info(
                        "Duplicate deal request replayed: idempotencyKey={} dealReference={}",
                        key,
                        existing.getDealReference());

                return new Result(mapper.toResponse(existing), true);
            }
        }

        DealConfirmationWriter.CreatedDeal created;

        try {

            created = writer.create(clerkUserId, request, key);

        } catch (DataIntegrityViolationException duplicate) {

            /*
             * Both copies of a double-clicked request passed the check above at
             * the same moment, and the unique index on (customer, key) let only
             * one of them through. The writer's transaction has already rolled
             * back — no units were taken twice — so answer with the deal that
             * won instead of failing the customer's retry.
             */
            DealConfirmation existing = key == null
                    ? null
                    : findByIdempotencyKey(clerkUserId, key);

            if (existing == null) {
                throw duplicate;
            }

            log.info(
                    "Concurrent duplicate deal request collapsed onto deal {}",
                    existing.getDealReference());

            return new Result(mapper.toResponse(existing), true);
        }

        generateDocument(created.documentData());

        return new Result(created.response(), false);
    }

    // =========================================================
    // DOCUMENT EXTENSION POINT
    // =========================================================

    /**
     * Hands the created deal to the document step.
     *
     * <p>Called after the deal has been committed, and outside any transaction.
     * Today the only implementation produces nothing, so this logs and returns;
     * when the Excel template and PDF steps arrive they plug in behind
     * {@link DealConfirmationDocumentService} without changing anything above.</p>
     *
     * <p>A failure here is logged, never propagated: the customer's purchase is
     * already confirmed and inventory already reserved, and failing the request
     * now would make the client believe the deal did not happen.</p>
     */
    private void generateDocument(DealConfirmationDocumentData dealData) {

        try {

            DealConfirmationDocument document = documentService.generate(dealData);

            if (!document.isPresent()) {
                return;
            }

            /*
             * TODO document workflow:
             *   1. store the document and keep its location on the deal,
             *   2. move the deal to DealConfirmationStatus.CONFIRMATION_GENERATED,
             *   3. notify the customer.
             * All three belong to the document implementation, not here.
             */
            log.info(
                    "Deal confirmation document produced for deal {}: fileName={} contentType={}",
                    dealData.dealReference(),
                    document.fileName(),
                    document.contentType());

        } catch (Exception failure) {

            log.error(
                    "Deal confirmation document generation failed for deal {}; the deal itself stands",
                    dealData.dealReference(),
                    failure);
        }
    }

    // =========================================================
    // IDEMPOTENCY
    // =========================================================

    /**
     * Treats a blank header as "no key" so a client that always sends the header
     * but sometimes empty does not make every request look like a retry of the
     * first one.
     */
    private String normalizeKey(String idempotencyKey) {

        if (idempotencyKey == null) {
            return null;
        }

        String trimmed = idempotencyKey.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private DealConfirmation findByIdempotencyKey(String clerkUserId, String key) {

        return dealConfirmationRepository
                .findByCustomer_ClerkUserIdAndIdempotencyKey(clerkUserId, key)
                .orElse(null);
    }
}
