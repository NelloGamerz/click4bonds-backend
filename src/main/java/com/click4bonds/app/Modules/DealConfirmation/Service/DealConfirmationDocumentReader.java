package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Model.DocumentFormat;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads back a deal's generated confirmation letter.
 *
 * <p>The counterpart to generation: it resolves the storage key recorded on the
 * deal into bytes, so the letter can be downloaded rather than only existing
 * somewhere on disk.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationDocumentReader {

    private final DealConfirmationRepository dealConfirmationRepository;
    private final DocumentStorage documentStorage;

    /**
     * A document ready to be sent to the customer.
     *
     * @param fileName    name to offer the browser
     * @param contentType MIME type
     * @param content     the bytes
     */
    public record DownloadedDocument(String fileName, String contentType, byte[] content) {
    }

    /**
     * Loads the confirmation letter for one of this customer's deals.
     *
     * @param customerId   the authenticated customer
     * @param dealReference the deal to read
     * @throws ResourceNotFoundException when the customer has no such deal, or
     *                                   the deal has no document yet. The two are
     *                                   reported identically on purpose: a deal
     *                                   belonging to someone else must not be
     *                                   distinguishable from one that does not
     *                                   exist.
     */
    public DownloadedDocument read(UUID customerId, String dealReference) {

        DealConfirmation deal = dealConfirmationRepository
                .findByDealReferenceAndCustomer_Id(dealReference, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deal not found: " + dealReference));

        if (deal.getDocumentPath() == null) {

            /*
             * A real deal with no letter. Reported as "not found" rather than as
             * an error because it is a legitimate state: document generation may
             * have failed, or may be switched off in this environment. The deal
             * itself is unaffected.
             */
            log.info(
                    "Deal {} has no generated document", dealReference);

            throw new ResourceNotFoundException(
                    "No document has been generated for deal: " + dealReference);
        }

        byte[] content = documentStorage.read(deal.getDocumentPath());

        /*
         * The stored path carries the format's extension, so the content type
         * comes from the same place the key did. Reading it from the key keeps
         * the two from being set independently and disagreeing.
         */
        DocumentFormat format = deal.getDocumentPath().endsWith("." + DocumentFormat.PDF.extension())
                ? DocumentFormat.PDF
                : DocumentFormat.XLSX;

        return new DownloadedDocument(
                dealReference + "." + format.extension(),
                format.contentType(),
                content);
    }
}
