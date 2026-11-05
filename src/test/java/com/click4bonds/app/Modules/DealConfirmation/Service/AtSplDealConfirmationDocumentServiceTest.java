package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

/**
 * The letter pipeline: draw the PDF and store it — and what happens when the
 * render fails.
 */
@ExtendWith(MockitoExtension.class)
class AtSplDealConfirmationDocumentServiceTest {

    private static final String REFERENCE = "DC-20260925-000001";
    private static final byte[] PDF = "pdf".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DealConfirmationPdfService pdfService;

    @Mock
    private DocumentStorage storage;

    private AtSplDealConfirmationDocumentService service;

    @BeforeEach
    void setUp() {

        service = new AtSplDealConfirmationDocumentService(
                pdfService,
                storage,
                new DealConfirmationSheetValuesFactory(
                        new DocumentProperties(), new GsecAccrualCalculator()),
                new DealConfirmationSheetStrategyFactory(
                        new PsuPrivateSaleSheetStrategy(),
                        new GsecSellSheetStrategy()));
    }

    // =========================================================
    // HAPPY PATH
    // =========================================================

    @Test
    void drawsTheCorporateLetterAndStoresItAsAPdf() {

        givenThePdfIsDrawn();

        DealConfirmationDocument document = service.generate(snapshot());

        verify(pdfService).render(any(), eq(DealConfirmationLayout.CORPORATE));

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
    void drawsTheGsecLetterAndStoresItAsAPdf() {

        givenThePdfIsDrawn();

        DealConfirmationDocument document = service.generate(snapshot("Sovereign"));

        verify(pdfService).render(any(), eq(DealConfirmationLayout.GSEC));

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

        verify(storage).store(key.capture(), anyString(), anyString(), any());

        assertTrue(
                key.getValue().startsWith("2026/09/"),
                "expected the September the deal settles in, got " + key.getValue());
    }

    // =========================================================
    // WHICH LAYOUT THE DEAL GETS
    // =========================================================

    @Test
    void printsARatedBondOnTheCorporateLetter() {

        givenThePdfIsDrawn();

        service.generate(snapshot("AAA"));

        verify(pdfService).render(any(), eq(DealConfirmationLayout.CORPORATE));
    }

    @Test
    void printsASovereignBondOnTheGsecLetter() {

        givenThePdfIsDrawn();

        service.generate(snapshot("Sovereign"));

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
    }

    // =========================================================
    // FAILURE
    // =========================================================

    @Test
    void refusesASovereignDealWhoseGsecFiguresCouldNotBeComputed() {

        /*
         * The G-Sec letter refuses a deal with no maturity or no coupon rather
         * than printing blanks. Checked before the render, so nothing is drawn
         * and nothing is stored.
         */
        assertThrows(
                DocumentGenerationException.class,
                () -> service.generate(sovereignWithNoMaturity()));

        verifyNoInteractions(pdfService, storage);
    }

    @Test
    void storesNothingWhenTheLetterCannotBeDrawn() {

        when(pdfService.render(any(), any()))
                .thenThrow(new DocumentGenerationException("the letter cannot be drawn"));

        assertThrows(DocumentGenerationException.class, () -> service.generate(snapshot()));

        /*
         * There is no intermediate artefact to keep, so a failed render leaves
         * nothing behind and the deal stays retryable.
         */
        verifyNoInteractions(storage);
    }

    @Test
    void refusesADealItCannotBuildValuesFor() {

        /*
         * Raised by the values factory before anything is drawn, so a deal
         * missing its price or accrued interest leaves no partial artefacts.
         */
        DealConfirmationDocumentData incomplete = new DealConfirmationDocumentData(
                REFERENCE,
                LocalDate.of(2026, 9, 25),
                null,
                LocalDate.of(2026, 9, 25),
                0,
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
                DealConfirmationStatus.CREATED,
                null,
                null,
                null,
                null,
                null);

        assertThrows(DocumentGenerationException.class, () -> service.generate(incomplete));

        verifyNoInteractions(pdfService, storage);
    }

    @Test
    void refusesASnapshotWithNoReference() {

        /*
         * The values factory no longer requires a reference, because the same
         * values are built for a view-only request describing a deal that was
         * never written. A letter is what needs one: it prints the reference and
         * is filed under it, so a reference-less snapshot would mean a key
         * reading "null.pdf". That guard lives here, at the layer that would
         * build it, and it is checked before anything is written.
         */
        DealConfirmationDocumentData noReference = snapshot(null);

        DealConfirmationDocumentData withoutReference = new DealConfirmationDocumentData(
                null,
                noReference.dealDate(),
                noReference.dealCreatedAt(),
                noReference.valueDate(),
                noReference.valueDateOffsetDays(),
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
                noReference.status(),
                noReference.counterparty(),
                noReference.deliveryMode(),
                noReference.settlementNumber(),
                noReference.side(),
                noReference.depository());

        assertThrows(
                DocumentGenerationException.class,
                () -> service.generate(withoutReference));

        verifyNoInteractions(pdfService, storage);
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private void givenThePdfIsDrawn() {

        when(pdfService.render(any(), any())).thenReturn(PDF);

        when(storage.store(anyString(), anyString(), anyString(), any()))
                .thenAnswer(call -> new StoredDocument(
                        call.getArgument(0),
                        call.getArgument(1),
                        call.getArgument(2),
                        ((byte[]) call.getArgument(3)).length));
    }

    private DealConfirmationDocumentData snapshot() {

        return snapshot(null);
    }

    /**
     * A Sovereign deal with no maturity: the G-Sec figures cannot be computed,
     * so the G-Sec layout refuses it.
     */
    private DealConfirmationDocumentData sovereignWithNoMaturity() {

        DealConfirmationDocumentData complete = snapshot("Sovereign");

        return new DealConfirmationDocumentData(
                complete.dealReference(),
                complete.dealDate(),
                complete.dealCreatedAt(),
                complete.valueDate(),
                complete.valueDateOffsetDays(),
                complete.customerName(),
                complete.customerEmail(),
                complete.bondName(),
                complete.isin(),
                complete.securityType(),
                complete.rating(),
                complete.semiYtm(),
                complete.annualYtm(),
                complete.couponRate(),
                null,
                complete.ipDateDescription(),
                complete.putCallDescription(),
                complete.previousCouponDate(),
                complete.accruedDays(),
                complete.accruedInterestPerHundredFace(),
                complete.principalPerLot(),
                complete.quantityPerLot(),
                complete.numberOfLots(),
                complete.totalQuantity(),
                complete.pricePerUnit(),
                complete.totalAmount(),
                complete.status(),
                complete.counterparty(),
                complete.deliveryMode(),
                complete.settlementNumber(),
                complete.side(),
                complete.depository());
    }

    private DealConfirmationDocumentData snapshot(String rating) {

        return new DealConfirmationDocumentData(
                REFERENCE,
                LocalDate.of(2026, 9, 25),
                null,
                LocalDate.of(2026, 9, 25),
                0,
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
                DealConfirmationStatus.CREATED,
                null,
                null,
                null,
                null,
                null);
    }
}
