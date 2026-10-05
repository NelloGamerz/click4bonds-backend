package com.click4bonds.app.Modules.DealConfirmation.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentKafkaConfig;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentRecorder;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentService;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

/**
 * What {@link DealConfirmationDocumentConsumer} does with a consumed request,
 * and what it is structurally allowed to do.
 *
 * <p>No broker is involved: the listener method is called directly.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationDocumentConsumerTest {

    private static final String REFERENCE = "DC-20260925-000001";
    private static final String STORAGE_KEY = "2026/09/" + REFERENCE + ".pdf";

    /** The listener method under test. */
    private static final String LISTENER_METHOD = "onDealConfirmationDocument";

    @Mock
    private DealConfirmationDocumentService documentService;

    @Mock
    private DealConfirmationDocumentRecorder documentRecorder;

    private DealConfirmationDocumentConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DealConfirmationDocumentConsumer(documentService, documentRecorder);
    }

    // =========================================================
    // HAPPY PATH
    // =========================================================

    @Test
    void rendersTheDealItWasGivenAndRecordsWhereItLanded() {

        DealConfirmationDocumentEvent event = event();

        when(documentService.generate(event.documentData()))
                .thenReturn(pdf());

        consumer.onDealConfirmationDocument(event);

        verify(documentService).generate(event.documentData());

        /*
         * Recording is keyed on the deal id, which is why it travels beside the
         * snapshot: the snapshot is keyed by the customer-facing reference and
         * the update is keyed by the primary key.
         */
        verify(documentRecorder).record(event.dealId(), STORAGE_KEY);
    }

    @Test
    void doesNotRecordAnythingWhenNoDocumentWasProduced() {

        /*
         * none() means documents are switched off in this environment, which is
         * a normal outcome rather than a failure. Recording it would mark every
         * deal as documented when no file exists.
         */
        DealConfirmationDocumentEvent event = event();

        when(documentService.generate(any())).thenReturn(DealConfirmationDocument.none());

        consumer.onDealConfirmationDocument(event);

        verify(documentRecorder, never()).record(any(), any());
    }

    // =========================================================
    // FAILURE
    // =========================================================

    @Test
    void aGenerationFailureIsSwallowed() {

        /*
         * It must not escape: the listener's container would redeliver the record,
         * and a deal that cannot be lettered — a G-Sec with no maturity, say —
         * would be retried forever and block its partition. The deal stays
         * CREATED with no document, which is what a failed generation left
         * behind before this moved off the request thread.
         */
        DealConfirmationDocumentEvent event = event();

        when(documentService.generate(any()))
                .thenThrow(new DocumentGenerationException("soffice is not installed"));

        consumer.onDealConfirmationDocument(event);

        verify(documentRecorder, never()).record(any(), any());
    }

    @Test
    void aRecordingFailureIsSwallowed() {

        DealConfirmationDocumentEvent event = event();

        when(documentService.generate(any())).thenReturn(pdf());

        org.mockito.Mockito.doThrow(new IllegalStateException("database is down"))
                .when(documentRecorder)
                .record(any(), any());

        consumer.onDealConfirmationDocument(event);

        /*
         * The letter exists in the bucket; only recording it failed. There is
         * nothing the customer can do about it and nothing a redelivery would
         * fix, so it is reported and dropped.
         */
        verify(documentRecorder).record(eq(event.dealId()), eq(STORAGE_KEY));
    }

    @Test
    void discardsARequestThatCarriesNoSnapshot() {

        consumer.onDealConfirmationDocument(null);
        consumer.onDealConfirmationDocument(new DealConfirmationDocumentEvent(
                UUID.randomUUID(),
                null));

        verifyNoInteractions(documentService);
        verifyNoInteractions(documentRecorder);
    }

    // =========================================================
    // WIRING
    // =========================================================

    @Test
    void listensOnTheTopicTheProducerPublishesTo() throws NoSuchMethodException {

        KafkaListener listener = listenerMethod().getAnnotation(KafkaListener.class);

        assertNotNull(listener, "The listener method must be annotated @KafkaListener");

        assertArrayEquals(
                new String[] {DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC},
                listener.topics(),
                "Producer and consumer must agree on the topic");
    }

    @Test
    void runsOnTheContainerFactoryThatKnowsItsPayloadType() throws NoSuchMethodException {

        /*
         * The shared consumer configuration is pinned to the analytics payload
         * type. Without naming this factory the deal's request would be
         * deserialised as an analytics event and discarded — and the application
         * would still start, so nothing else would catch it.
         */
        KafkaListener listener = listenerMethod().getAnnotation(KafkaListener.class);

        assertEquals(
                "dealDocumentListenerContainerFactory",
                listener.containerFactory(),
                "The default factory deserialises the wrong payload type");
    }

    @Test
    void doesNotHoldTheKafkaTemplateItself() {

        /*
         * It only consumes. A template here would be a second, accidental way to
         * publish — and a loop waiting to happen, since a listener that republishes
         * its own topic is not obviously wrong until it is.
         */
        for (java.lang.reflect.Field field : DealConfirmationDocumentConsumer.class.getDeclaredFields()) {
            assertFalse(
                    KafkaTemplate.class.isAssignableFrom(field.getType()),
                    "The consumer must not hold a template: " + field.getName());
        }
    }

    private static java.lang.reflect.Method listenerMethod() throws NoSuchMethodException {
        return DealConfirmationDocumentConsumer.class.getMethod(
                LISTENER_METHOD,
                DealConfirmationDocumentEvent.class);
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private static DealConfirmationDocument pdf() {

        return new DealConfirmationDocument(
                REFERENCE + ".pdf",
                "application/pdf",
                "pdf bytes".getBytes(),
                STORAGE_KEY);
    }

    private static DealConfirmationDocumentEvent event() {

        return new DealConfirmationDocumentEvent(
                UUID.randomUUID(),
                new DealConfirmationDocumentData(
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
                        DealConfirmationStatus.CREATED));
    }
}
