package com.click4bonds.app.Modules.DealConfirmation.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;
import com.click4bonds.app.Modules.Document.Model.DocumentFormat;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;
import com.click4bonds.app.Modules.Document.Service.DocumentKeys;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces the ATSPL deal confirmation letter.
 *
 * <p>The real implementation behind {@link DealConfirmationDocumentService}. It
 * draws the letter straight from the computed values with
 * {@link DealConfirmationPdfService} — no spreadsheet, no external converter —
 * and stores the PDF. It knows nothing about how a deal is created: the snapshot
 * it is handed is complete.</p>
 *
 * <p><strong>Which layout a deal gets is not decided here.</strong> A
 * Sovereign-rated bond is confirmed on the G-Sec layout and everything else on
 * the corporate one; {@link DealConfirmationSheetStrategyFactory} makes that
 * choice, and the {@link DealConfirmationSheetStrategy} it returns supplies the
 * layout and refuses a deal missing figures that layout cannot print. This class
 * is only the pipeline around it.</p>
 *
 * <p><strong>Deliberately not annotated {@code @Service}.</strong> It is declared
 * as a {@code @Bean} in
 * {@link com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentConfig},
 * alongside the no-op it is selected against, so that which implementation is
 * wired is decided in one readable place.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class AtSplDealConfirmationDocumentService implements DealConfirmationDocumentService {

    private final DealConfirmationPdfService pdfService;
    private final DocumentStorage storage;
    private final DealConfirmationSheetValuesFactory valuesFactory;
    private final DealConfirmationSheetStrategyFactory sheetStrategyFactory;

    @Override
    public DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation) {

        requireReference(dealConfirmation);

        DealConfirmationSheetValues values = valuesFactory.build(dealConfirmation);

        /*
         * Which letter this deal is printed on. A Sovereign-rated bond gets the
         * G-Sec layout; every other deal gets the corporate one. The choice is
         * made from the rating carried on the snapshot, because the bond itself
         * is no longer reachable here.
         */
        DealConfirmationSheetStrategy strategy =
                sheetStrategyFactory.strategyFor(dealConfirmation.rating());

        /*
         * A layout can name figures it cannot print without — the G-Sec accrued
         * interest, say. Refuse before drawing rather than print a blank letter.
         */
        strategy.requireFigures(values);

        /*
         * Filed under the value date, which is the date the trade settles and
         * the date the letter is about.
         */
        String stem = DocumentKeys.stemForDeal(
                values.dealReference(),
                values.valueDate());

        byte[] pdf = pdfService.render(values, strategy.layout());

        String pdfKey = stem + "." + DocumentFormat.PDF.extension();

        StoredDocument storedPdf = store(
                pdfKey,
                values.dealReference(),
                DocumentFormat.PDF,
                pdf);

        log.info(
                "Deal confirmation letter generated for deal {}: key={} pdfBytes={}",
                values.dealReference(),
                storedPdf.storageKey(),
                pdf.length);

        return new DealConfirmationDocument(
                storedPdf.fileName(),
                storedPdf.contentType(),
                pdf,
                storedPdf.storageKey());
    }

    /**
     * Refuses a snapshot with no reference.
     *
     * <p>Checked here rather than in
     * {@link DealConfirmationSheetValuesFactory}, which no longer requires one:
     * the same values are built for a view-only request describing a deal that was
     * never written, and that has no reference to give. A letter is different —
     * the reference is printed on it and the PDF is filed under it, so a letter
     * without one is a blank cell and a key reading {@code null.pdf}. This is the
     * layer that would produce both, so this is where the requirement belongs.</p>
     *
     * @throws DocumentGenerationException when there is no reference to file the
     *         letter under
     */
    private void requireReference(DealConfirmationDocumentData dealConfirmation) {

        if (dealConfirmation == null) {
            throw new DocumentGenerationException("No deal snapshot was supplied");
        }

        String reference = dealConfirmation.dealReference();

        if (reference == null || reference.isBlank()) {
            throw new DocumentGenerationException(
                    "Deal has no reference, so no letter can be produced for it");
        }
    }

    private StoredDocument store(
            String storageKey,
            String dealReference,
            DocumentFormat format,
            byte[] content) {

        return storage.store(
                storageKey,
                dealReference + "." + format.extension(),
                format.contentType(),
                content);
    }
}
