package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;
import com.click4bonds.app.Modules.Document.Model.DocumentFormat;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;
import com.click4bonds.app.Modules.Document.Service.PdfConverter;
import com.click4bonds.app.Modules.Document.Service.XlsxTemplateWriter;

import java.nio.charset.StandardCharsets;

/**
 * The letter pipeline: fill, render, store — and what happens when one of those
 * steps fails.
 */
@ExtendWith(MockitoExtension.class)
class AtSplDealConfirmationDocumentServiceTest {

    private static final String REFERENCE = "DC-20260925-000001";
    private static final byte[] XLSX = "spreadsheet".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF = "pdf".getBytes(StandardCharsets.UTF_8);

    @Mock
    private XlsxTemplateWriter templateWriter;

    @Mock
    private PdfConverter pdfConverter;

    @Mock
    private DealConfirmationPdfService pdfService;

    @Mock
    private DocumentStorage storage;

    private DocumentProperties properties;

    private AtSplDealConfirmationDocumentService service;

    @BeforeEach
    void setUp() {

        properties = new DocumentProperties();

        service = new AtSplDealConfirmationDocumentService(
                templateWriter,
                pdfConverter,
                pdfService,
                storage,
                new DealConfirmationSheetValuesFactory(properties, new GsecAccrualCalculator()),
                new DealConfirmationSheetStrategyFactory(
                        new PsuPrivateSaleSheetStrategy(properties),
                        new GsecSellSheetStrategy(properties)),
                properties,
                /*
                 * A direct executor, so the upload and the render run on the
                 * calling thread in the order they are submitted. The stages are
                 * still submitted and awaited exactly as they are in production —
                 * only the pool is removed, which is what makes the interaction
                 * with storage and the converter verifiable here.
                 */
                Runnable::run);
    }

    // =========================================================
    // HAPPY PATH
    // =========================================================

    @Test
    void drawsTheCorporateLetterDirectlyAndStoresOnlyThePdf() {

        givenThePdfIsDrawn();

        DealConfirmationDocument document = service.generate(snapshot());

        /*
         * The corporate letter no longer goes through a spreadsheet: it is
         * drawn from the values and stored as a PDF, so neither the template
         * writer nor the LibreOffice converter is touched.
         */
        verify(pdfService).render(any(), eq(DealConfirmationLayout.CORPORATE));
        verifyNoInteractions(templateWriter, pdfConverter);

        verify(storage).store(
                eq("2026/09/" + REFERENCE + ".pdf"),
                eq(REFERENCE + ".pdf"),
                eq(DocumentFormat.PDF.contentType()),
                eq(PDF));

        assertEquals(REFERENCE + ".pdf", document.fileName());
        assertEquals(DocumentFormat.PDF.contentType(), document.contentType());
        assertEquals("2026/09/" + REFERENCE + ".pdf", document.storageKey());
        assertArrayEquals(PDF, document.content());
        assertTrue(document.isPresent());
    }

    @Test
    void drawsTheGsecLetterDirectlyAndStoresOnlyThePdf() {

        givenThePdfIsDrawn();

        DealConfirmationDocument document = service.generate(snapshot("Sovereign"));

        /*
         * Same design, different figures: the G-Sec layout is now drawn from
         * the values like the corporate letter, with no spreadsheet and no
         * LibreOffice in the path.
         */
        verify(pdfService).render(any(), eq(DealConfirmationLayout.GSEC));
        verifyNoInteractions(templateWriter, pdfConverter);

        verify(storage).store(
                eq("2026/09/" + REFERENCE + ".pdf"),
                eq(REFERENCE + ".pdf"),
                eq(DocumentFormat.PDF.contentType()),
                eq(PDF));

        assertEquals(REFERENCE + ".pdf", document.fileName());
        assertArrayEquals(PDF, document.content());
    }

    @Test
    void filesTheLetterUnderTheValueDate() {

        givenThePdfIsDrawn();

        service.generate(snapshot());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        verify(storage, org.mockito.Mockito.atLeastOnce())
                .store(key.capture(), anyString(), anyString(), any());

        assertTrue(
                key.getValue().startsWith("2026/09/"),
                "expected the September the deal settles in, got " + key.getValue());
    }

    @Test
    void fillsTheGsecSheetWhenPdfRenderingIsDisabled() {

        properties.getPdf().setEnabled(false);

        givenTheSpreadsheetIsFilled();

        service.generate(snapshot("Sovereign"));

        /*
         * The sheet name is configurable because the workbook has two sale
         * sheets. If the configured name were wrong the fill would throw, but
         * asserting the wiring here makes a config regression obvious.
         */
        verify(templateWriter).fill(
                eq(properties.getTemplate().getGsecSheetName()),
                anyMap(),
                anyMap());
    }

    // =========================================================
    // WHICH LAYOUT THE DEAL GETS
    // =========================================================

    @Test
    void printsARatedBondOnTheCorporateLetter() {

        givenThePdfIsDrawn();

        service.generate(snapshot("AAA"));

        /*
         * Anything that is not a Sovereign keeps the letter it has always had.
         * The corporate letter is now drawn directly, so the layout is selected
         * by proving the renderer was used and the G-Sec sheet was not.
         */
        verify(pdfService).render(any(), eq(DealConfirmationLayout.CORPORATE));
        verifyNoInteractions(templateWriter, pdfConverter);
    }

    @Test
    void printsASovereignBondOnTheGsecLetter() {

        givenThePdfIsDrawn();

        service.generate(snapshot("Sovereign"));

        /*
         * The whole point of the routing: a government security is drawn on the
         * G-Sec layout, not the corporate one. The figures that layout prints
         * are pinned in GsecSellSheetStrategyTest.
         */
        verify(pdfService).render(any(), eq(DealConfirmationLayout.GSEC));
    }

    @Test
    void matchesTheSovereignRatingRegardlessOfCaseOrSurroundingSpace() {

        givenThePdfIsDrawn();

        /*
         * The rating is free text typed into an admin form, so it arrives in
         * whatever case the operator used. The layout must not depend on that.
         */
        service.generate(snapshot(" sovereign "));

        verify(pdfService).render(any(), eq(DealConfirmationLayout.GSEC));
    }

    @Test
    void printsAnUnratedBondOnTheCorporateLetter() {

        givenThePdfIsDrawn();

        /*
         * A bond with no rating recorded keeps the letter every deal had before
         * the G-Sec layout existed. Treating a missing rating as Sovereign would
         * move every unrated bond onto a letter with TDS on it, silently.
         */
        service.generate(snapshot(null));

        verify(pdfService).render(any(), eq(DealConfirmationLayout.CORPORATE));
        verifyNoInteractions(templateWriter, pdfConverter);
    }

    // =========================================================
    // PDF SWITCHED OFF
    // =========================================================

    @Test
    void storesTheSpreadsheetOnlyWhenPdfRenderingIsDisabled() {

        properties.getPdf().setEnabled(false);

        givenTheSpreadsheetIsFilled();

        DealConfirmationDocument document = service.generate(snapshot());

        /*
         * A deliberate degraded mode, not a failure: the spreadsheet is the
         * document, and it can be rendered elsewhere. The caller still gets a
         * present document, so the deal is recorded as documented.
         */
        verify(storage).store(
                eq("2026/09/" + REFERENCE + ".xlsx"),
                anyString(),
                eq(DocumentFormat.XLSX.contentType()),
                eq(XLSX));

        verifyNoInteractions(pdfConverter, pdfService);

        assertEquals(DocumentFormat.XLSX.contentType(), document.contentType());
        assertTrue(document.isPresent());
    }

    // =========================================================
    // FAILURE
    // =========================================================

    @Test
    void storesNothingWhenTheSpreadsheetCannotBeFilled() {

        properties.getPdf().setEnabled(false);

        when(templateWriter.fill(anyString(), any(), any()))
                .thenThrow(new DocumentGenerationException("template is missing"));

        assertThrows(
                DocumentGenerationException.class,
                () -> service.generate(snapshot("Sovereign")));

        /*
         * Nothing half-written: if the fill failed there is no spreadsheet to
         * keep and no PDF to render.
         */
        verifyNoInteractions(storage);
        verifyNoInteractions(pdfConverter, pdfService);
    }

    @Test
    void storesNothingWhenTheLetterCannotBeDrawn() {

        when(pdfService.render(any(), any()))
                .thenThrow(new DocumentGenerationException("the letter cannot be drawn"));

        assertThrows(DocumentGenerationException.class, () -> service.generate(snapshot()));

        /*
         * The direct route has no intermediate artefact to keep, so a failed
         * render leaves nothing behind and the deal stays retryable.
         */
        verifyNoInteractions(storage);
        verifyNoInteractions(templateWriter, pdfConverter);
    }

    @Test
    void keepsTheSpreadsheetWhenRenderingFailsInTheDegradedMode() {

        /*
         * With PDF rendering switched off there is no render step, so a stored
         * spreadsheet is the whole document. This pins that the degraded path
         * still stores rather than silently producing nothing.
         */
        properties.getPdf().setEnabled(false);

        givenTheSpreadsheetIsFilled();

        assertEquals(
                DocumentFormat.XLSX.contentType(),
                service.generate(snapshot("Sovereign")).contentType());

        verify(storage).store(
                eq("2026/09/" + REFERENCE + ".xlsx"),
                anyString(),
                anyString(),
                any());

        verifyNoInteractions(pdfService, pdfConverter);
    }

    @Test
    void refusesADealItCannotPrintAFullConsiderationFor() {

        /*
         * Raised before anything is written, so a deal missing its accrued
         * interest leaves no partial artefacts behind.
         */
        DealConfirmationDocumentData incomplete = new DealConfirmationDocumentData(
                REFERENCE,
                LocalDate.of(2026, 9, 25),
                null,
                LocalDate.of(2026, 9, 25),
                "Test Customer",
                "customer@example.com",
                "TEST BOND 2027",
                "INE123A07012",
                "SECURED",
                null,
                null,
                null,
                new BigDecimal("13.70"),
                LocalDate.of(2027, 8, 23),
                "23rd Of Every Month",
                null,
                null,
                null,
                null,
                null,
                100L,
                5L,
                11L,
                new BigDecimal("100.00"),
                new BigDecimal("1100.00"),
                DealConfirmationStatus.CREATED);

        assertThrows(DocumentGenerationException.class, () -> service.generate(incomplete));

        verifyNoInteractions(storage);
        verifyNoInteractions(templateWriter, pdfConverter, pdfService);
    }

    @Test
    void refusesASnapshotWithNoReference() {

        /*
         * The values factory no longer requires a reference, because the same
         * values are built for a view-only request describing a deal that was
         * never written. A letter is what needs one: it prints the reference in A5
         * and files both artefacts under it, so a reference-less snapshot would
         * mean a blank cell and a key reading "null.pdf". That guard lives here,
         * at the layer that would build both, and it is checked before anything is
         * written.
         */
        DealConfirmationDocumentData noReference = snapshot(null);

        DealConfirmationDocumentData withoutReference = new DealConfirmationDocumentData(
                null,
                noReference.dealDate(),
                noReference.dealCreatedAt(),
                noReference.valueDate(),
                noReference.customerName(),
                noReference.customerEmail(),
                noReference.bondName(),
                noReference.isin(),
                noReference.securityType(),
                noReference.rating(),
                noReference.semiYtm(),
                noReference.annualYtm(),
                noReference.couponRate(),
                noReference.maturityDate(),
                noReference.ipDateDescription(),
                noReference.putCallDescription(),
                noReference.previousCouponDate(),
                noReference.accruedDays(),
                noReference.accruedInterestPerHundredFace(),
                noReference.principalPerLot(),
                noReference.quantityPerLot(),
                noReference.numberOfLots(),
                noReference.totalQuantity(),
                noReference.pricePerUnit(),
                noReference.totalAmount(),
                noReference.status());

        assertThrows(
                DocumentGenerationException.class,
                () -> service.generate(withoutReference));

        verifyNoInteractions(storage);
        verifyNoInteractions(templateWriter, pdfConverter, pdfService);
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    /**
     * The corporate letter: a bond with no rating, which is what every deal was
     * before the G-Sec layout existed.
     */
    private DealConfirmationDocumentData snapshot() {

        return snapshot(null);
    }

    private void givenTheSpreadsheetIsFilled() {

        when(templateWriter.fill(anyString(), any(), any())).thenReturn(XLSX);
    }

    private void givenThePdfIsDrawn() {

        when(pdfService.render(any(), any())).thenReturn(PDF);

        givenDocumentsAreStored();
    }

    private void givenDocumentsAreStored() {

        when(storage.store(anyString(), anyString(), anyString(), any()))
                .thenAnswer(call -> new StoredDocument(
                        call.getArgument(0),
                        call.getArgument(1),
                        call.getArgument(2),
                        ((byte[]) call.getArgument(3)).length));
    }

    private DealConfirmationDocumentData snapshot(String rating) {

        return new DealConfirmationDocumentData(
                REFERENCE,
                LocalDate.of(2026, 9, 25),
                null,
                LocalDate.of(2026, 9, 25),
                "Test Customer",
                "customer@example.com",
                "TEST BOND 2027",
                "INE123A07012",
                "SECURED",
                rating,
                null,
                null,
                new BigDecimal("13.70"),
                LocalDate.of(2027, 8, 23),
                "23rd Of Every Month",
                null,
                LocalDate.of(2026, 7, 23),
                64L,
                new BigDecimal("2.401"),
                new BigDecimal("100.00"),
                100L,
                5L,
                11L,
                new BigDecimal("100.00"),
                new BigDecimal("1100.00"),
                DealConfirmationStatus.CREATED);
    }
}
