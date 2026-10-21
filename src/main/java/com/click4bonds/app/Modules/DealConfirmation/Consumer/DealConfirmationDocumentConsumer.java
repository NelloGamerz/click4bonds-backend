package com.click4bonds.app.Modules.DealConfirmation.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentKafkaConfig;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentRecorder;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentService;
import com.click4bonds.app.Modules.Email.Model.EmailAttachment;
import com.click4bonds.app.Modules.Email.Service.EmailService;

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
 *
 * <p><strong>The rendered letter is emailed to the customer.</strong> Once the
 * PDF exists in the store, the same bytes are attached to a confirmation email.
 * That send is deliberately split off from the document work below: a bounced
 * or rejected email must not undo — or look like — a failed generation, so it
 * has its own try/catch, and a failure is logged while the deal keeps its
 * recorded document.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationDocumentConsumer {

    private final DealConfirmationDocumentService documentService;
    private final DealConfirmationDocumentRecorder documentRecorder;
    private final EmailService emailService;

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

            emailConfirmation(dealData, document);

        } catch (Exception failure) {

            log.error(
                    "Deal confirmation document generation failed for deal {}; the deal itself stands",
                    dealData.dealReference(),
                    failure);
        }
    }

    /**
     * Sends the letter that was just generated to the customer.
     *
     * <p>Runs after the document is recorded, so the email can never be the
     * reason a produced letter goes unrecorded. A customer with no address on
     * file is skipped rather than failed: the letter still exists in the store,
     * and a missing email is not something a redelivery would fix.</p>
     *
     * @param dealData the deal the letter belongs to
     * @param document the generated letter, whose bytes are attached
     */
    private void emailConfirmation(
            DealConfirmationDocumentData dealData,
            DealConfirmationDocument document) {

        String to = dealData.customerEmail();

        if (to == null || to.isBlank()) {

            log.warn(
                    "Deal confirmation letter for deal {} was not emailed: the customer has no email address",
                    dealData.dealReference());

            return;
        }

        try {

            emailService.sendDealConfirmationEmail(
                    to,
                    dealData.customerName(),
                    dealData.dealReference(),
                    new EmailAttachment(
                            document.fileName(),
                            document.contentType(),
                            document.content()));

            log.info(
                    "Deal confirmation letter emailed to {} for deal {}",
                    to,
                    dealData.dealReference());

        } catch (Exception failure) {

            /*
             * The document is stored and recorded; only its delivery failed.
             * Rethrowing would redeliver the message and regenerate the letter
             * for an address the provider will reject again.
             */
            log.error(
                    "Deal confirmation letter for deal {} could not be emailed to {}",
                    dealData.dealReference(),
                    to,
                    failure);
        }
    }
}
