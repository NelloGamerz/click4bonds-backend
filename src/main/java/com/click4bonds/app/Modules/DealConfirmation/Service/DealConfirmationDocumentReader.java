package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
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
    private final DealConfirmationDocumentService documentService;
    private final DealConfirmationWriter writer;

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

        return toDocument(
                dealConfirmationRepository
                        .findByDealReferenceAndCustomer_Id(dealReference, customerId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Deal not found: " + dealReference)),
                dealReference);
    }

    /**
     * Loads the confirmation letter for a deal the given employee created.
     *
     * @param employeeId    the authenticated staff account
     * @param dealReference the deal to read
     * @throws ResourceNotFoundException when the employee has no such deal, or
     *                                   the deal has no document yet — the same
     *                                   two answers as {@link #read}, reported
     *                                   identically for the same reason
     */
    public DownloadedDocument readCreatedBy(UUID employeeId, String dealReference) {

        return toDocument(
                dealConfirmationRepository
                        .findByDealReferenceAndCreatedBy_Id(dealReference, employeeId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Deal not found: " + dealReference)),
                dealReference);
    }

    /**
     * Loads the confirmation letter for any deal. Only for an {@code ADMIN},
     * who may read every deal's letter.
     *
     * @param dealReference the deal to read
     * @throws ResourceNotFoundException when there is no such deal, or it has no
     *                                   document yet
     */
    public DownloadedDocument readAny(String dealReference) {

        return toDocument(
                dealConfirmationRepository
                        .findByDealReference(dealReference)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Deal not found: " + dealReference)),
                dealReference);
    }

    private DownloadedDocument toDocument(
            DealConfirmation deal,
            String dealReference) {

        /*
         * A deal whose letter has not been produced yet is lettered now, from
         * the deal as it stands, and the bytes are returned without recording
         * them: the stored copy and CONFIRMATION_GENERATED stay the document
         * step's to set, so a preview download cannot claim a deal is lettered.
         */
        if (deal.getStatus() != DealConfirmationStatus.CONFIRMATION_GENERATED
                || deal.getDocumentR2Path() == null) {

            return generateOnDemand(deal, dealReference);
        }

        /*
         * A deal row records the document's address, not its key. Resolving it
         * here — rather than storing a key — is what makes the row readable, and
         * it fails loudly if the address names a different endpoint or bucket than
         * the one configured, instead of silently fetching from the wrong place.
         */
        byte[] content = documentStorage.read(documentStorage.keyOf(deal.getDocumentR2Path()));

        /*
         * The address carries the format's extension, so the content type comes
         * from the same place the address did. Reading it from the address keeps
         * the two from being set independently and disagreeing.
         */
        DocumentFormat format = deal.getDocumentR2Path().endsWith("." + DocumentFormat.PDF.extension())
                ? DocumentFormat.PDF
                : DocumentFormat.XLSX;

        return new DownloadedDocument(
                dealReference + "." + format.extension(),
                format.contentType(),
                content);
    }

    /**
     * Renders the deal's letter right now and returns it, without recording it.
     *
     * @throws ResourceNotFoundException when documents are switched off in this
     *                                   environment, so nothing was produced
     */
    private DownloadedDocument generateOnDemand(
            DealConfirmation deal,
            String dealReference) {

        DealConfirmationDocument document =
                documentService.generate(writer.documentDataFor(deal.getId()));

        if (!document.isPresent()) {

            log.info(
                    "Deal {} has no generated document and documents are switched off",
                    dealReference);

            throw new ResourceNotFoundException(
                    "No document has been generated for deal: " + dealReference);
        }

        return new DownloadedDocument(
                document.fileName(),
                document.contentType(),
                document.content());
    }
}
