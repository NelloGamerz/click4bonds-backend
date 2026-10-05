package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

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
 * <p><strong>Which sheet it fills is not decided here.</strong> A Sovereign-rated
 * bond is confirmed on the G-Sec sell sheet and everything else on the corporate
 * PSU sheet; {@link DealConfirmationSheetStrategyFactory} makes that choice and
 * the {@link DealConfirmationSheetStrategy} it returns supplies the sheet name,
 * the cells and any number-format overrides. This class is only the pipeline
 * around it — fill, render, store — so adding a third layout does not touch
 * it.</p>
 *
 * <p><strong>The spreadsheet is always stored before the PDF is.</strong> The
 * workbook is filled, then uploaded and rendered at the same time — the render
 * reads the bytes in memory, so it never waits on the upload. The upload is
 * still waited on first: a deal whose spreadsheet did not land is reported as a
 * storage failure with no PDF stored against it, and a render failure leaves the
 * filled workbook in the bucket for an operator to convert by hand. The deal is
 * unlettered and retryable rather than silently half-documented.</p>
 *
 * <p><strong>It runs on the document executor, not the caller's thread.</strong>
 * The two stages are submitted to a pool sized for exactly this shape of work;
 * see {@code DocumentTaskExecutorConfig}. Since this class is called from a Kafka
 * listener rather than a web request, blocking on the pool costs nothing a
 * customer is waiting for.</p>
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
    private final DealConfirmationSheetStrategyFactory sheetStrategyFactory;
    private final DocumentProperties properties;
    private final Executor documentExecutor;

    @Override
    public DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation) {

        DealConfirmationSheetValues values = valuesFactory.build(dealConfirmation);

        /*
         * Which sheet this deal is printed on. A Sovereign-rated bond gets the
         * G-Sec layout; every other deal gets the corporate one it has always
         * had. The choice is made from the rating carried on the snapshot,
         * because the bond itself is no longer reachable here.
         */
        DealConfirmationSheetStrategy strategy =
                sheetStrategyFactory.strategyFor(dealConfirmation.rating());

        Map<String, Object> cells = strategy.toCells(values);

        /*
         * Filed under the value date, which is the date the trade settles and
         * the date the letter is about.
         */
        String stem = DocumentKeys.stemForDeal(
                values.dealReference(),
                values.valueDate());

        byte[] xlsx = templateWriter.fill(
                strategy.sheetName(),
                cells,
                strategy.numberFormats());

        String xlsxKey = stem + "." + DocumentFormat.XLSX.extension();

        /*
         * The upload and the render are independent once the workbook exists:
         * the render is handed the bytes in memory, never the stored object, so
         * it does not have to wait for the upload and the upload does not have
         * to wait for LibreOffice. Running them together overlaps a network
         * round trip with a subprocess, and the slow one no longer pays for the
         * fast one.
         *
         * The upload is started first, and awaited first below, so a deal whose
         * spreadsheet never landed is reported as a storage failure with no PDF
         * stored against it. The render may still be running at that point; its
         * result is simply never read, which wastes one conversion on a path
         * that has already failed.
         */
        CompletableFuture<StoredDocument> xlsxUpload = CompletableFuture.supplyAsync(
                () -> store(
                        xlsxKey,
                        values.dealReference(),
                        DocumentFormat.XLSX,
                        xlsx),
                documentExecutor);

        if (!properties.getPdf().isEnabled()) {

            /*
             * Deliberately not a failure. The spreadsheet is the document; the
             * PDF is a rendering of it, and an environment without LibreOffice
             * still produces a usable letter that can be converted elsewhere.
             */
            await(xlsxUpload);

            log.info(
                    "PDF rendering is disabled; stored the spreadsheet only for deal {}",
                    values.dealReference());

            return new DealConfirmationDocument(
                    values.dealReference() + "." + DocumentFormat.XLSX.extension(),
                    DocumentFormat.XLSX.contentType(),
                    xlsx,
                    xlsxKey);
        }

        CompletableFuture<byte[]> pdfRender = CompletableFuture.supplyAsync(
                () -> pdfConverter.toPdf(xlsx, values.dealReference()),
                documentExecutor);

        /*
         * The spreadsheet is waited on before the render is read, so a render
         * failure still leaves a filled workbook in the bucket for an operator
         * to convert by hand — and a failed upload is reported as itself rather
         * than hidden behind a render result that happens to be ready first.
         */
        await(xlsxUpload);

        byte[] pdf = await(pdfRender);

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

    /**
     * Waits for a stage of the pipeline, reporting its failure as itself.
     *
     * <p>A bare {@link CompletableFuture#join()} wraps whatever went wrong in a
     * {@link CompletionException}, which would turn a
     * {@code DocumentGenerationException} — the type the rest of this module
     * knows how to read, and the type the tests assert on — into something
     * unrecognisable one layer up. The cause is unwrapped so a failed render
     * still arrives as a failed render.</p>
     *
     * @throws RuntimeException the failure the stage itself raised
     */
    private <T> T await(CompletableFuture<T> stage) {

        try {

            return stage.join();

        } catch (CompletionException wrapped) {

            Throwable cause = wrapped.getCause();

            if (cause instanceof RuntimeException failure) {
                throw failure;
            }

            throw wrapped;
        }
    }
}
