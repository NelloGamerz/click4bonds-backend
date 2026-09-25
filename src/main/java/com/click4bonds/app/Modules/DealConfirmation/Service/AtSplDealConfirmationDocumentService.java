package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.Map;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Model.DocumentFormat;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;
import com.click4bonds.app.Modules.Document.Service.DocumentKeys;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;
import com.click4bonds.app.Modules.Document.Service.PdfConverter;
import com.click4bonds.app.Modules.Document.Service.XlsxTemplateWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces the ATSPL deal confirmation letter.
 *
 * <p>The real implementation behind {@link DealConfirmationDocumentService}: it
 * fills {@code ATSPL Deal Format.xlsx}, renders a PDF from it, and stores both.
 * It is the only class that knows a deal confirmation is a spreadsheet, and it
 * knows nothing about how a deal is created — the snapshot it is handed is
 * complete.</p>
 *
 * <p><strong>Order of operations matters.</strong> The spreadsheet is filled and
 * stored first, then rendered. If rendering fails, the filled spreadsheet is
 * still on disk for an operator to convert by hand, and the deal is left
 * unlettered and retryable rather than silently half-documented.</p>
 *
 * <p><strong>Both artefacts share one key stem</strong>, so regenerating a deal
 * overwrites its previous documents instead of accumulating copies.</p>
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

    private final XlsxTemplateWriter templateWriter;
    private final PdfConverter pdfConverter;
    private final DocumentStorage storage;
    private final DealConfirmationSheetValuesFactory valuesFactory;
    private final DealConfirmationCellMap cellMap;
    private final DocumentProperties properties;

    @Override
    public DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation) {

        DealConfirmationSheetValues values = valuesFactory.build(dealConfirmation);

        Map<String, Object> cells = cellMap.toCells(values);

        /*
         * Filed under the value date, which is the date the trade settles and
         * the date the letter is about.
         */
        String stem = DocumentKeys.stemForDeal(
                values.dealReference(),
                values.valueDate());

        byte[] xlsx = templateWriter.fill(
                properties.getTemplate().getSheetName(),
                cells);

        String xlsxKey = stem + "." + DocumentFormat.XLSX.extension();

        store(xlsxKey, values.dealReference(), DocumentFormat.XLSX, xlsx);

        if (!properties.getPdf().isEnabled()) {

            /*
             * Deliberately not a failure. The spreadsheet is the document; the
             * PDF is a rendering of it, and an environment without LibreOffice
             * still produces a usable letter that can be converted elsewhere.
             */
            log.info(
                    "PDF rendering is disabled; stored the spreadsheet only for deal {}",
                    values.dealReference());

            return new DealConfirmationDocument(
                    values.dealReference() + "." + DocumentFormat.XLSX.extension(),
                    DocumentFormat.XLSX.contentType(),
                    xlsx,
                    xlsxKey);
        }

        byte[] pdf = pdfConverter.toPdf(xlsx, values.dealReference());

        /*
         * The PDF is what the customer receives and what the deal records as its
         * document, so it is stored last: reaching this line means both
         * artefacts exist.
         */
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
