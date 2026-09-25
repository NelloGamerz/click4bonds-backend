package com.click4bonds.app.Modules.DealConfirmation.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;

/**
 * The deal confirmation document step.
 *
 * <p>Fills the ATSPL confirmation letter and renders it as a PDF. Two
 * implementations are wired by
 * {@link com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentConfig}:</p>
 *
 * <pre>
 * DealConfirmation
 *        |
 *        v
 * DealConfirmationSheetValuesFactory   (snapshot -> the letter's values)
 *        |
 *        v
 * DealConfirmationCellMap              (values -> cells of the template)
 *        |
 *        v
 * XlsxTemplateWriter                   (fills ATSPL Deal Format.xlsx)
 *        |
 *        v
 * PdfConverter                         (LibreOffice, headless)
 *        |
 *        v
 * DocumentStorage                      (keeps both artefacts)
 *        |
 *        v
 * download / email
 * </pre>
 *
 * <p>{@link AtSplDealConfirmationDocumentService} is the real one.
 * {@link NoOpDealConfirmationDocumentService} produces nothing and is wired when
 * {@code document.enabled=false}, for environments with no LibreOffice.</p>
 *
 * <p><strong>Contract.</strong> Implementations run after the deal's transaction
 * has committed (a slow document step must not hold a database transaction
 * open), so they must not read the database or touch lazy associations. They get
 * {@link DealConfirmationDocumentData}, which is already a complete snapshot —
 * including the interest figures, which are computed inside that transaction
 * precisely because the services behind them need a live entity.</p>
 *
 * <p><strong>Failure.</strong> A failure to generate a document must not fail or
 * roll back the deal: the purchase is already confirmed. Implementations throw,
 * and the caller logs it and leaves the deal in
 * {@code DealConfirmationStatus.CREATED} for a later retry. Returning
 * {@link DealConfirmationDocument#none()} is <em>not</em> a way to report a
 * failure — it means "documents are switched off", and using it for an error
 * would hide the problem and leave no log.</p>
 */
public interface DealConfirmationDocumentService {

    /**
     * Produces the confirmation document for a created deal.
     *
     * @param dealConfirmation complete snapshot of the deal to document
     * @return the document, or {@link DealConfirmationDocument#none()} when no
     *         document was produced
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException
     *         when the document could not be produced
     */
    DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation);
}
