package com.click4bonds.app.Modules.DealConfirmation.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentKafkaConfig;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentRecorder;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces a deal's confirmation letter, off the request thread.
 *
 * <p>This is the half that used to run inside {@code POST /api/deal-confirmations}.
 * Filling a spreadsheet, rendering it with LibreOffice and uploading two
 * artefacts is seconds of work, and a customer was made to wait through all of
 * it for an answer that only needed the deal row to exist. Now the request ends
 * when the deal is committed and this listener does the slow part.</p>
 *
 * <p><strong>It is the only caller of the document step.</strong> The two
 * collaborators it uses were both written to be called from somewhere other than
 * a web request: {@link DealConfirmationDocumentService} takes a complete
 * snapshot and is forbidden from touching the database, and
 * {@link DealConfirmationDocumentRecorder} is a short transaction of its own
 * precisely so a caller outside one can record where the letter landed.</p>
 *
 * <p><strong>A failure is logged, never rethrown.</strong> This matches what the
 * module did before the move, and it is what keeps a poison message from
 * blocking its partition: a deal that cannot be lettered — a bond with no
 * maturity on the G-Sec layout, say — would otherwise be redelivered forever.
 * The deal stays {@code CREATED} with no document, exactly as a failed
 * generation left it before, so a future re-run can still pick it up.</p>
 *
 * <p><strong>Reads under the container factory named here, not the default.</strong>
 * The shared consumer configuration is pinned to the analytics payload type, so
 * this listener needs the factory that knows about
 * {@link DealConfirmationDocumentEvent} — see
 * {@link DealConfirmationDocumentKafkaConfig}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationDocumentConsumer {

    private final DealConfirmationDocumentService documentService;
    private final DealConfirmationDocumentRecorder documentRecorder;

    /**
     * Renders one deal's letter and records where it landed.
     *
     * @param event the deal and the snapshot its letter is built from; a missing
     *              snapshot is discarded rather than treated as a failure, since
     *              there is nothing to retry
     */
    @KafkaListener(
            topics = DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
            containerFactory = "dealDocumentListenerContainerFactory")
    public void onDealConfirmationDocument(DealConfirmationDocumentEvent event) {

        if (event == null || event.documentData() == null) {

            log.warn(
                    "Discarded a document request carrying no deal snapshot from {}",
                    DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC);

            return;
        }

        DealConfirmationDocumentData dealData = event.documentData();

        log.info(
                "Generating the deal confirmation document for deal {}",
                dealData.dealReference());

        try {

            DealConfirmationDocument document = documentService.generate(dealData);

            /*
             * none() means documents are switched off in this environment, which
             * is a normal outcome rather than a failure. Recording it would mark
             * every deal as documented when no file exists.
             */
            if (!document.isPresent()) {
                return;
            }

            documentRecorder.record(event.dealId(), document.storageKey());

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
}
