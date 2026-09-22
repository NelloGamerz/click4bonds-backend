package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * Retry handling: a double click, a network retry or a frontend retry must not
 * buy the bond twice.
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationServiceTest {

    private static final String ISIN = "INE123A01016";
    private static final String CLERK_ID = "user_2abc";
    private static final String KEY = "key-1";

    @Mock
    private DealConfirmationWriter writer;

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @Mock
    private DealConfirmationDocumentService documentService;

    private DealConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new DealConfirmationService(
                writer,
                dealConfirmationRepository,
                new DealConfirmationMapper(),
                documentService);
    }

    // ============================================================
    // NO KEY
    // ============================================================

    @Test
    void withoutAKeyEveryRequestCreatesADeal() {

        givenWriterCreatesDeal();

        DealConfirmationService.Result result =
                service.createDeal(CLERK_ID, request(), null);

        assertFalse(result.replayed());
        assertEquals("DC-20260922-000001", result.response().getDealReference());

        verify(dealConfirmationRepository, never())
                .findByCustomer_ClerkUserIdAndIdempotencyKey(any(), any());
    }

    @Test
    void aBlankKeyIsTreatedAsNoKey() {

        /*
         * A client that always sends the header, sometimes empty, must not have
         * every request after the first mistaken for a retry.
         */
        givenWriterCreatesDeal();

        DealConfirmationService.Result result =
                service.createDeal(CLERK_ID, request(), "   ");

        assertFalse(result.replayed());

        verify(dealConfirmationRepository, never())
                .findByCustomer_ClerkUserIdAndIdempotencyKey(any(), any());
    }

    // ============================================================
    // REPLAY
    // ============================================================

    @Test
    void aRetryWithTheSameKeyReturnsTheOriginalDeal() {

        DealConfirmation original = deal("DC-20260922-000001");

        when(dealConfirmationRepository
                .findByCustomer_ClerkUserIdAndIdempotencyKey(CLERK_ID, KEY))
                .thenReturn(Optional.of(original));

        DealConfirmationService.Result result =
                service.createDeal(CLERK_ID, request(), KEY);

        assertTrue(result.replayed());
        assertEquals("DC-20260922-000001", result.response().getDealReference());

        /*
         * The retry reserved nothing: it never reached the transactional writer.
         */
        verify(writer, never()).create(any(), any(), any());
        verify(dealConfirmationRepository, never()).save(any());
    }

    @Test
    void aReplayDoesNotRegenerateTheDocument() {

        DealConfirmation original = deal("DC-20260922-000001");

        when(dealConfirmationRepository
                .findByCustomer_ClerkUserIdAndIdempotencyKey(CLERK_ID, KEY))
                .thenReturn(Optional.of(original));

        service.createDeal(CLERK_ID, request(), KEY);

        verify(documentService, never()).generate(any());
    }

    @Test
    void aRetryIsScopedToTheCustomerWhoSentTheKey() {

        /*
         * Two customers may pick the same key; one must never be handed the
         * other's deal. The lookup is what enforces that, so this pins the
         * arguments it is called with.
         */
        when(dealConfirmationRepository
                .findByCustomer_ClerkUserIdAndIdempotencyKey("someone_else", KEY))
                .thenReturn(Optional.empty());

        givenWriterCreatesDeal();

        service.createDeal("someone_else", request(), KEY);

        verify(dealConfirmationRepository)
                .findByCustomer_ClerkUserIdAndIdempotencyKey("someone_else", KEY);
    }

    // ============================================================
    // CONCURRENT DUPLICATE
    // ============================================================

    @Test
    void aSimultaneousDuplicateIsCollapsedOntoTheWinningDeal() {

        /*
         * Both copies of a double click passed the lookup at the same instant.
         * The unique index on (customer, key) let one through and rejected the
         * other, whose transaction rolled back — so no units were taken twice,
         * and the customer is answered with the deal that did get created.
         */
        DealConfirmation winner = deal("DC-20260922-000001");

        when(writer.create(eq(CLERK_ID), any(), eq(KEY)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        when(dealConfirmationRepository
                .findByCustomer_ClerkUserIdAndIdempotencyKey(CLERK_ID, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));

        DealConfirmationService.Result result =
                service.createDeal(CLERK_ID, request(), KEY);

        assertTrue(result.replayed());
        assertEquals("DC-20260922-000001", result.response().getDealReference());
    }

    @Test
    void aConstraintViolationWithNoKeyIsNotSwallowed() {

        when(writer.create(eq(CLERK_ID), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.createDeal(CLERK_ID, request(), null));

        verify(dealConfirmationRepository, never())
                .findByCustomer_ClerkUserIdAndIdempotencyKey(any(), any());
    }

    // ============================================================
    // DOCUMENT EXTENSION POINT
    // ============================================================

    @Test
    void handsTheCreatedDealToTheDocumentStep() {

        givenWriterCreatesDeal();

        service.createDeal(CLERK_ID, request(), null);

        verify(documentService).generate(any(DealConfirmationDocumentData.class));
    }

    @Test
    void aDocumentFailureDoesNotFailThePurchase() {

        /*
         * The deal is committed by the time the document step runs. Reporting a
         * failure now would tell the customer their purchase did not happen when
         * it did.
         */
        givenWriterReturnsCreatedDeal();

        when(documentService.generate(any()))
                .thenThrow(new IllegalStateException("template missing"));

        DealConfirmationService.Result result =
                service.createDeal(CLERK_ID, request(), null);

        assertEquals("DC-20260922-000001", result.response().getDealReference());
        assertFalse(result.replayed());
    }

    // ============================================================
    // FIXTURES
    // ============================================================

    private CreateDealConfirmationRequest request() {
        return new CreateDealConfirmationRequest(ISIN, 100L, 5L);
    }

    private void givenWriterCreatesDeal() {

        givenWriterReturnsCreatedDeal();

        when(documentService.generate(any()))
                .thenReturn(DealConfirmationDocument.none());
    }

    private void givenWriterReturnsCreatedDeal() {

        DealConfirmation deal = deal("DC-20260922-000001");

        when(writer.create(any(), any(), any()))
                .thenReturn(new DealConfirmationWriter.CreatedDeal(
                        new DealConfirmationMapper().toResponse(deal),
                        DealConfirmationDocumentData.from(deal)));
    }

    private DealConfirmation deal(String reference) {

        User customer = User.builder()
                .id(UUID.randomUUID())
                .clerkUserId(CLERK_ID)
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
