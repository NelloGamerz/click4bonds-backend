package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Producer.DealConfirmationDocumentProducer;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Entry point for buying a bond.
 *
 * <p>Wraps the transactional {@link DealConfirmationWriter} with the two things
 * that must happen outside its transaction: recognising a retried request, and
 * asking for the confirmation document to be produced.</p>
 *
 * <p><strong>The document step is no longer performed here.</strong> Filling a
 * spreadsheet, rendering it with LibreOffice and uploading two artefacts is
 * seconds of work, and the customer was made to wait through all of it for an
 * answer that only needed the deal row to exist. The request now ends as soon as
 * the deal is committed: the response carries every figure the letter prints, and
 * a request to produce the letter is published to Kafka, where
 * {@code DealConfirmationDocumentConsumer} picks it up.</p>
 *
 * <p>This class is deliberately not transactional. Building the response's
 * letter values and publishing to Kafka are both things that must not happen
 * inside the writer's transaction, and the publish is asynchronous in any
 * case.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationService {

    private final DealConfirmationWriter writer;
    private final DealConfirmationRepository dealConfirmationRepository;
    private final DealConfirmationMapper mapper;
    private final DealConfirmationSheetValuesFactory sheetValuesFactory;
    private final DealConfirmationDocumentProducer documentProducer;
    private final DocumentProperties documentProperties;

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
     * @param userId    authenticated customer, from the JWT
     * @param request        bond and quantities
     * @param idempotencyKey client-supplied key, or null when the client sent none
     * @param viewOnly  when true the request is answered without being acted on:
     *                  the deal is validated and its figures are computed, but
     *                  nothing is written and no letter is asked for — see
     *                  {@link #preview}
     */
    public Result createDeal(
            UUID userId,
            CreateDealConfirmationRequest request,
            String idempotencyKey,
            boolean viewOnly) {

        if (viewOnly) {
            return preview(userId, request);
        }

        String key = normalizeKey(idempotencyKey);

        if (key != null) {

            DealConfirmation existing = findByIdempotencyKey(userId, key);

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

            created = writer.create(userId, request, key);

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
                    : findByIdempotencyKey(userId, key);

            if (existing == null) {
                throw duplicate;
            }

            log.info(
                    "Concurrent duplicate deal request collapsed onto deal {}",
                    existing.getDealReference());

            return new Result(mapper.toResponse(existing), true);
        }

        DealConfirmationResponse response = created.response();

        /*
         * The figures the letter prints travel back with the deal, so the
         * frontend can show the customer what they bought without waiting for a
         * document that is now produced asynchronously.
         */
        response.setLetterValues(letterValues(created.documentData()));

        requestDocument(created.response().getDealConfirmationId(), created.documentData());

        return new Result(response, false);
    }

    /**
     * Answers what the deal would be, writing nothing.
     *
     * <p>The request is validated and the figures are computed exactly as a
     * purchase would compute them — same bond, same lot size, same value date,
     * same accrual — but no row is written, no units are reserved, no reference
     * is issued and no letter is asked for. The caller is looking at a deal, not
     * making one.</p>
     *
     * <p><strong>Idempotency does not apply here.</strong> The key exists to stop
     * one intended purchase becoming two rows, and this writes no row at all. The
     * lookup is skipped rather than reused because it would answer with a deal
     * that <em>was</em> written — a real reference, a real letter — for a caller
     * that asked to see what a deal would look like. A caller that previews and
     * then buys sends no key on the preview and its own key on the purchase.</p>
     *
     * <p>The response carries no reference, no id, no status and no timestamp,
     * because there is no deal for them to describe. Everything a customer reads
     * — the bond, the quantities, the money, and the same {@code letterValues} a
     * confirmation letter would print — is filled in.</p>
     *
     * @return the projected deal, always {@code replayed = false}: nothing was
     *         created, and nothing was replayed either
     */
    private Result preview(UUID userId, CreateDealConfirmationRequest request) {

        DealConfirmationWriter.CreatedDeal projected = writer.preview(userId, request);

        DealConfirmationResponse response = projected.response();

        response.setLetterValues(letterValues(projected.documentData()));

        return new Result(response, false);
    }

    // =========================================================
    // DOCUMENT STEP
    // =========================================================

    /**
     * Computes the figures the confirmation letter will print.
     *
     * <p>Best-effort: the same factory the letter is built from refuses a deal
     * with no price or no accrued interest, and the same refusal must not fail a
     * purchase the customer has already made. A deal that cannot be lettered
     * simply answers without these figures.</p>
     *
     * @return the letter's values, or null when the deal cannot support them
     */
    private DealConfirmationSheetValues letterValues(DealConfirmationDocumentData dealData) {

        try {

            return sheetValuesFactory.build(dealData);

        } catch (RuntimeException cannotBePrinted) {

            log.warn(
                    "Letter values could not be computed for deal {}; the response carries none",
                    dealData.dealReference(),
                    cannotBePrinted);

            return null;
        }
    }

    /**
     * Asks for the deal's confirmation letter to be produced.
     *
     * <p>Called after the deal has been committed: publishing a request for a
     * row that then rolls back would letter a deal that does not exist. The send
     * is asynchronous, so this returns as soon as the record is buffered and the
     * customer is answered immediately.</p>
     *
     * <p>The snapshot travels on the message rather than a deal id to look up,
     * because the document step has always run outside the creating transaction
     * and may not read the database — see {@link DealConfirmationDocumentData}.</p>
     *
     * <p>A failure here is logged, never propagated: the purchase is already
     * confirmed and its inventory already reserved, and failing the request now
     * would tell the customer their deal did not happen when it did. The deal
     * stays {@code CREATED} with no document, which is what a failed generation
     * left behind before this moved to Kafka.</p>
     */
    private void requestDocument(UUID dealId, DealConfirmationDocumentData dealData) {

        /*
         * With documents switched off there is no letter to ask for, and
         * publishing anyway would fill the topic with requests whose only
         * possible outcome is the no-op implementation declining them.
         */
        if (!documentProperties.isEnabled()) {

            log.debug(
                    "Documents are switched off; no letter requested for deal {}",
                    dealData.dealReference());

            return;
        }

        try {

            documentProducer.publish(new DealConfirmationDocumentEvent(dealId, dealData));

        } catch (RuntimeException failure) {

            log.error(
                    "Deal {} was created but its document request could not be published;"
                            + " the deal itself stands",
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

    private DealConfirmation findByIdempotencyKey(UUID userId, String key) {

        return dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(userId, key)
                .orElse(null);
    }
}
