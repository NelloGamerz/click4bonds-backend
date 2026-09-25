package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private DocumentStorage storage;

    private DocumentProperties properties;

    private AtSplDealConfirmationDocumentService service;

    @BeforeEach
    void setUp() {

        properties = new DocumentProperties();

        service = new AtSplDealConfirmationDocumentService(
                templateWriter,
                pdfConverter,
                storage,
                new DealConfirmationSheetValuesFactory(properties),
                new DealConfirmationCellMap(properties),
                properties);
    }

    // =========================================================
    // HAPPY PATH
    // =========================================================

    @Test
    void storesBothArtefactsUnderOneStemAndReturnsThePdf() {

        givenTheSpreadsheetIsFilled();
        givenThePdfRenders();

        DealConfirmationDocument document = service.generate(snapshot());

        /*
         * One stem for both formats, so regenerating a deal replaces a matched
         * pair rather than leaving the previous spreadsheet beside the new PDF.
         */
        verify(storage).store(
                eq("2026/09/" + REFERENCE + ".xlsx"),
                eq(REFERENCE + ".xlsx"),
                eq(DocumentFormat.XLSX.contentType()),
                eq(XLSX));

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
    void filesTheLetterUnderTheValueDate() {

        givenTheSpreadsheetIsFilled();
        givenThePdfRenders();

        service.generate(snapshot());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        verify(storage, org.mockito.Mockito.atLeastOnce())
                .store(key.capture(), anyString(), anyString(), any());

        assertTrue(
                key.getValue().startsWith("2026/09/"),
                "expected the September the deal settles in, got " + key.getValue());
    }

    @Test
    void fillsTheSheetTheTemplateConfigNames() {

        givenTheSpreadsheetIsFilled();
        givenThePdfRenders();

        service.generate(snapshot());

        /*
         * The sheet name is configurable because the workbook has two sale
         * sheets. If the configured name were wrong the fill would throw, but
         * asserting the wiring here makes a config regression obvious.
         */
        verify(templateWriter).fill(eq(properties.getTemplate().getSheetName()), anyMap());
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

        verifyNoInteractions(pdfConverter);

        assertEquals(DocumentFormat.XLSX.contentType(), document.contentType());
        assertTrue(document.isPresent());
    }

    // =========================================================
    // FAILURE
    // =========================================================

    @Test
    void storesNothingWhenTheSpreadsheetCannotBeFilled() {

        when(templateWriter.fill(anyString(), any()))
                .thenThrow(new DocumentGenerationException("template is missing"));

        assertThrows(DocumentGenerationException.class, () -> service.generate(snapshot()));

        /*
         * Nothing half-written: if the fill failed there is no spreadsheet to
         * keep and no PDF to render.
         */
        verifyNoInteractions(storage);
        verifyNoInteractions(pdfConverter);
    }

    @Test
    void keepsTheSpreadsheetWhenRenderingFails() {

        givenTheSpreadsheetIsFilled();

        when(pdfConverter.toPdf(any(), anyString()))
                .thenThrow(new DocumentGenerationException("soffice is not installed"));

        assertThrows(DocumentGenerationException.class, () -> service.generate(snapshot()));

        /*
         * The spreadsheet is stored before rendering is attempted, so a render
         * failure still leaves an operator something to convert by hand. Only
         * the PDF is missing, and the caller records nothing, so the deal stays
         * retryable.
         */
        verify(storage).store(
                eq("2026/09/" + REFERENCE + ".xlsx"),
                anyString(),
                anyString(),
                any());

        verify(storage, never()).store(
                eq("2026/09/" + REFERENCE + ".pdf"),
                anyString(),
                anyString(),
                any());
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
                new BigDecimal("13.70"),
                LocalDate.of(2027, 8, 23),
                "23rd Of Every Month",
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
        verifyNoInteractions(templateWriter);
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private void givenTheSpreadsheetIsFilled() {

        when(templateWriter.fill(anyString(), any())).thenReturn(XLSX);
    }

    private void givenThePdfRenders() {

        when(pdfConverter.toPdf(any(), anyString())).thenReturn(PDF);

        when(storage.store(anyString(), anyString(), anyString(), any()))
                .thenAnswer(call -> new StoredDocument(
                        call.getArgument(0),
                        call.getArgument(1),
                        call.getArgument(2),
                        ((byte[]) call.getArgument(3)).length));
    }

    private DealConfirmationDocumentData snapshot() {

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
                new BigDecimal("13.70"),
                LocalDate.of(2027, 8, 23),
                "23rd Of Every Month",
                LocalDate.of(2026, 7, 23),
                64L,
                new BigDecimal("2.401"),
                100L,
                5L,
                11L,
                new BigDecimal("100.00"),
                new BigDecimal("1100.00"),
                DealConfirmationStatus.CREATED);
    }
}
