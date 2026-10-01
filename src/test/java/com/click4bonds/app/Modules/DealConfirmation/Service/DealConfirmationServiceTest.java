package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Producer.DealConfirmationDocumentProducer;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * Retry handling: a double click, a network retry or a frontend retry must not
 * buy the bond twice — and the request must end at the deal, not at the letter.
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationServiceTest {

    private static final String ISIN = "INE123A01016";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String KEY = "key-1";

    @Mock
    private DealConfirmationWriter writer;

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @Mock
    private DealConfirmationSheetValuesFactory sheetValuesFactory;

    @Mock
    private DealConfirmationDocumentProducer documentProducer;

    private DocumentProperties documentProperties;

    private DealConfirmationService service;

    @BeforeEach
    void setUp() {

        documentProperties = new DocumentProperties();

        service = new DealConfirmationService(
                writer,
                dealConfirmationRepository,
                new DealConfirmationMapper(),
                sheetValuesFactory,
                documentProducer,
                documentProperties);
    }

    // ============================================================
    // NO KEY
    // ============================================================

    @Test
    void withoutAKeyEveryRequestCreatesADeal() {

        givenWriterCreatesDeal();

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), null);

        assertFalse(result.replayed());
        assertEquals("DC-20260922-000001", result.response().getDealReference());

        verify(dealConfirmationRepository, never())
                .findByCustomer_IdAndIdempotencyKey(any(), any());
    }

    @Test
    void aBlankKeyIsTreatedAsNoKey() {

        /*
         * A client that always sends the header, sometimes empty, must not have
         * every request after the first mistaken for a retry.
         */
        givenWriterCreatesDeal();

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), "   ");

        assertFalse(result.replayed());

        verify(dealConfirmationRepository, never())
                .findByCustomer_IdAndIdempotencyKey(any(), any());
    }

    // ============================================================
    // REPLAY
    // ============================================================

    @Test
    void aRetryWithTheSameKeyReturnsTheOriginalDeal() {

        DealConfirmation original = deal("DC-20260922-000001");

        when(dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(original));

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), KEY);

        assertTrue(result.replayed());
        assertEquals("DC-20260922-000001", result.response().getDealReference());

        /*
         * The retry created nothing: it never reached the transactional writer,
         * so no second deal and no second reference were produced.
         */
        verify(writer, never()).create(any(), any(), any());
        verify(dealConfirmationRepository, never()).save(any());
    }

    @Test
    void aReplayDoesNotAskForASecondDocument() {

        /*
         * The retry bought nothing, so the letter for the deal it replayed is
         * already requested — or already produced. Asking again would render a
         * second time, overwrite the same key, and tell the customer their
         * document is new when it is not.
         */
        DealConfirmation original = deal("DC-20260922-000001");

        when(dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(original));

        service.createDeal(USER_ID, request(), KEY);

        verify(documentProducer, never()).publish(any());
    }

    @Test
    void aReplayCarriesNoLetterValues() {

        /*
         * The figures are computed inside the creating transaction, from the
         * interest schedule as it stood then. Reconstructing them here, outside
         * any transaction and against a bond that may have been edited since,
         * would risk showing a different number from the one the customer was
         * given the first time.
         */
        DealConfirmation original = deal("DC-20260922-000001");

        when(dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(original));

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), KEY);

        assertNull(result.response().getLetterValues());
        verify(sheetValuesFactory, never()).build(any());
    }

    @Test
    void aRetryIsScopedToTheCustomerWhoSentTheKey() {

        /*
         * Two customers may pick the same key; one must never be handed the
         * other's deal. The lookup is what enforces that, so this pins the
         * arguments it is called with.
         */
        when(dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(OTHER_USER_ID, KEY))
                .thenReturn(Optional.empty());

        givenWriterCreatesDeal();

        service.createDeal(OTHER_USER_ID, request(), KEY);

        verify(dealConfirmationRepository)
                .findByCustomer_IdAndIdempotencyKey(OTHER_USER_ID, KEY);
    }

    // ============================================================
    // CONCURRENT DUPLICATE
    // ============================================================

    @Test
    void aSimultaneousDuplicateIsCollapsedOntoTheWinningDeal() {

        /*
         * Both copies of a double click passed the lookup at the same instant.
         * The unique index on (customer, key) let one through and rejected the
         * other, whose transaction rolled back — so no second deal was written,
         * and the customer is answered with the deal that did get created.
         */
        DealConfirmation winner = deal("DC-20260922-000001");

        when(writer.create(eq(USER_ID), any(), eq(KEY)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        when(dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), KEY);

        assertTrue(result.replayed());
        assertEquals("DC-20260922-000001", result.response().getDealReference());
    }

    @Test
    void aConstraintViolationWithNoKeyIsNotSwallowed() {

        when(writer.create(eq(USER_ID), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.createDeal(USER_ID, request(), null));

        verify(dealConfirmationRepository, never())
                .findByCustomer_IdAndIdempotencyKey(any(), any());
    }

    // ============================================================
    // THE DOCUMENT IS REQUESTED, NOT PRODUCED
    // ============================================================

    @Test
    void asksForTheDocumentToBeProducedRatherThanProducingIt() {

        /*
         * The whole point of the split. The request publishes a request and ends;
         * filling the template, rendering it and uploading two artefacts happens
         * on the other side of the topic, where a customer is not waiting for it.
         */
        givenWriterCreatesDeal();

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), null);

        ArgumentCaptor<DealConfirmationDocumentEvent> published =
                ArgumentCaptor.forClass(DealConfirmationDocumentEvent.class);

        verify(documentProducer).publish(published.capture());

        DealConfirmationDocumentEvent event = published.getValue();

        /*
         * The snapshot is what the consumer renders from, and it may not read the
         * database — so every figure has to travel on the message.
         */
        assertEquals(
                result.response().getDealConfirmationId(),
                event.dealId());
        assertEquals(
                "DC-20260922-000001",
                event.documentData().dealReference());
        assertNotNull(event.documentData().valueDate());
    }

    @Test
    void asksForNothingWhenDocumentsAreSwitchedOff() {

        /*
         * With generation switched off there is no letter to ask for. Publishing
         * anyway would fill the topic with requests whose only possible outcome
         * is the no-op implementation declining them.
         */
        documentProperties.setEnabled(false);

        givenWriterCreatesDeal();

        service.createDeal(USER_ID, request(), null);

        verify(documentProducer, never()).publish(any());
    }

    @Test
    void aPublishFailureDoesNotFailThePurchase() {

        /*
         * The deal is committed by the time the request is published. Reporting a
         * failure now would tell the customer their purchase did not happen when
         * it did — the deal stands, and only the letter is missing.
         */
        givenWriterCreatesDeal();

        org.mockito.Mockito.doThrow(new IllegalStateException("broker is down"))
                .when(documentProducer)
                .publish(any());

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), null);

        assertEquals("DC-20260922-000001", result.response().getDealReference());
        assertFalse(result.replayed());
    }

    // ============================================================
    // THE RESPONSE CARRIES THE LETTER'S FIGURES
    // ============================================================

    @Test
    void answersWithTheFiguresTheLetterPrints() {

        /*
         * The frontend renders the confirmation from these, so they have to be in
         * the response rather than only in the spreadsheet the customer will
         * eventually download.
         */
        givenWriterCreatesDeal();

        DealConfirmationSheetValues values = sheetValues();

        when(sheetValuesFactory.build(any())).thenReturn(values);

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), null);

        assertSame(values, result.response().getLetterValues());
    }

    @Test
    void answersWithoutFiguresWhenTheDealCannotSupportThem() {

        /*
         * The factory refuses a deal with no price or no accrued interest,
         * because a letter with a hole in it is worse than no letter. That is a
         * reason not to letter the deal — never a reason to fail a purchase the
         * customer has already made.
         */
        givenWriterCreatesDeal();

        when(sheetValuesFactory.build(any()))
                .thenThrow(new IllegalStateException("no accrued interest"));

        DealConfirmationService.Result result =
                service.createDeal(USER_ID, request(), null);

        assertEquals("DC-20260922-000001", result.response().getDealReference());
        assertNull(result.response().getLetterValues());
    }

    // ============================================================
    // FIXTURES
    // ============================================================

    private CreateDealConfirmationRequest request() {
        return new CreateDealConfirmationRequest(ISIN, 5L);
    }

    private DealConfirmationSheetValues sheetValues() {

        return new DealConfirmationSheetValues(
                LocalDate.of(2026, 9, 22),
                "DC-20260922-000001",
                "Counterparty Name- Test Customer",
                "Our Sale",
                "ICCL",
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 9, 22),
                ISIN,
                null,
                "Test Bond",
                null,
                null,
                null,
                null,
                null,
                500L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private void givenWriterCreatesDeal() {

        DealConfirmation deal = deal("DC-20260922-000001");

        when(writer.create(any(), any(), any()))
                .thenReturn(new DealConfirmationWriter.CreatedDeal(
                        new DealConfirmationMapper().toResponse(deal),
                        DealConfirmationDocumentData.from(
                                deal,
                                LocalDate.of(2026, 9, 22),
                                null)));
    }

    private DealConfirmation deal(String reference) {

        User customer = User.builder()
                .id(USER_ID)
                .email("customer@example.com")
                .build();

        Bond bond = Bond.builder()
                .id(UUID.randomUUID())
                .name("Test Bond")
                .isin(ISIN)
                .build();

        return DealConfirmation.builder()
                .id(UUID.randomUUID())
                .dealReference(reference)
                .customer(customer)
                .bond(bond)
                .isin(ISIN)
                .quantityPerLot(100L)
                .numberOfLots(5L)
                .totalQuantity(500L)
                .status(DealConfirmationStatus.CREATED)
                .build();
    }
}
