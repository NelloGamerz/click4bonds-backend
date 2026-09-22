package com.click4bonds.app.Modules.DealConfirmation.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;

/**
 * Extension point for the deal confirmation document.
 *
 * <p><strong>Not implemented yet, and intentionally so.</strong> The planned
 * pipeline is:</p>
 *
 * <pre>
 * DealConfirmation
 *        |
 *        v
 * ExcelTemplateService   (fills src/main/resources/deal_confirmation/Deal Format.xlsx)
 *        |
 *        v
 * Excel to PDF
 *        |
 *        v
 * DocumentStorage        (keeps the bytes somewhere durable)
 *        |
 *        v
 * download / email
 * </pre>
 *
 * <p>Only this interface and the {@link DealConfirmationDocumentData} snapshot
 * are in the module today. Nothing else in the deal flow knows about Excel, PDF,
 * or Apache POI — filling the template is a matter of adding one implementation
 * of this interface, with no change to deal creation, validation or inventory
 * handling.</p>
 *
 * <p><strong>Contract.</strong> Implementations run after the deal's transaction
 * has committed (a slow document step must not hold a database transaction
 * open), so they must not read the database or touch lazy associations. They get
 * {@link DealConfirmationDocumentData}, which is already a complete snapshot.</p>
 *
 * <p><strong>Failure.</strong> A failure to generate a document must not fail or
 * roll back the deal: the purchase is already confirmed. Implementations should
 * throw, and the caller logs and leaves the deal in
 * {@code DealConfirmationStatus.CREATED} for a later retry.</p>
 */
public interface DealConfirmationDocumentService {

    /**
     * Produces the confirmation document for a created deal.
     *
     * @param dealConfirmation complete snapshot of the deal to document
     * @return the document, or {@link DealConfirmationDocument#none()} when no
     *         document was produced
     */
    DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation);
}
