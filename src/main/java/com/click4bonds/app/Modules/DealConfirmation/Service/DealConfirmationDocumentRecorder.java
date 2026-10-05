package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Links a generated document to the deal it was produced for.
 *
 * <p>A bean of its own because it must be transactional while its caller is not.
 * The document step runs after the deal's transaction has committed, and
 * deliberately so — rendering a PDF is slow and holding a database transaction
 * open across it would pin a connection for the duration. Recording the result
 * is a separate, short transaction; a self-invocation inside the caller would
 * silently bypass Spring's proxy.</p>
 *
 * <p>The write itself is one statement keyed on the primary key, so it reads
 * nothing and loads no entity — the document contract forbids touching the
 * database from the document step, and this is the narrow exception that makes
 * the outcome durable.</p>
 *
 * <p><strong>It records the document's address, not its key.</strong> The
 * generator hands over the key, which is meaningful only alongside the store's
 * configuration; what a deal row stores is the address — endpoint, bucket and key
 * — so an operator reading the row can find the letter without being told how the
 * application is configured. This is the only place the conversion happens on the
 * way in; {@link DealConfirmationDocumentReader} converts back on the way out.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationDocumentRecorder {

    private final DealConfirmationRepository dealConfirmationRepository;
    private final DocumentStorage documentStorage;

    /**
     * Marks a deal as having a document.
     *
     * <p>Only called once the document is actually stored, so a deal is never
     * recorded as documented without a file behind it.</p>
     *
     * @param dealId     the deal to update
     * @param storageKey key the document was stored under
     */
    @Transactional
    public void record(UUID dealId, String storageKey) {

        if (dealId == null) {
            log.warn("Cannot record document {}: no deal id was supplied", storageKey);

            return;
        }

        /*
         * Resolved after the id check so that a missing id, which is a wiring
         * mistake, does not also demand that storage be configured — and so a
         * caller with no deal to update is not failed by a store it never needed.
         */
        String location = documentStorage.locationOf(storageKey);

        Instant recordedAt = Instant.now();

        int updated = dealConfirmationRepository.recordDocument(
                dealId,
                DealConfirmationStatus.CONFIRMATION_GENERATED,
                location,
                recordedAt);

        if (updated == 0) {

            /*
             * Logged rather than thrown. The caller already swallows document
             * failures, so throwing would add no signal — and a deal row that
             * vanished between being written and being documented is an
             * operational anomaly worth investigating, not a reason to report a
             * document failure to a purchase that succeeded.
             */
            log.warn(
                    "Document {} was stored but no deal with id {} was found to record it against",
                    location,
                    dealId);

            return;
        }

        log.info(
                "Deal {} recorded as documented: location={} status={}",
                dealId,
                location,
                DealConfirmationStatus.CONFIRMATION_GENERATED);
    }
}
