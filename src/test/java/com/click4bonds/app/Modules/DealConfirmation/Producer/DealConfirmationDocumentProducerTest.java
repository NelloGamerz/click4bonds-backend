package com.click4bonds.app.Modules.DealConfirmation.Producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentKafkaConfig;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;

/**
 * What {@link DealConfirmationDocumentProducer} puts on the wire.
 *
 * <p>The template is mocked, so no broker is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationDocumentProducerTest {

    @Mock
    private KafkaTemplate<String, DealConfirmationDocumentEvent> kafkaTemplate;

    private DealConfirmationDocumentProducer producer;

    @BeforeEach
    void setUp() {
        producer = new DealConfirmationDocumentProducer(kafkaTemplate);
    }

    @Test
    void publishesToTheDocumentTopicKeyedByTheDealId() {

        DealConfirmationDocumentEvent event = event();

        producer.publish(event);

        verify(kafkaTemplate).send(
                DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
                event.dealId().toString(),
                event);
    }

    @Test
    void publishesToTheTopicTheConsumerListensOn() {

        /*
         * Guards the pairing: changing one side without the other would
         * publish every deal's letter request to a topic nobody reads, and the
         * symptom would be silent — deals that simply never get documents.
         */
        assertEquals(
                "click4bonds.deal-confirmation.document",
                DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC);
    }

    @Test
    void usesEachDealsOwnIdAsTheKey() {

        /*
         * The key decides the partition, so keying by the deal id is what makes
         * two requests for the same deal arrive in order — which matters when
         * one of them is a regeneration.
         */
        DealConfirmationDocumentEvent first = event();
        DealConfirmationDocumentEvent second = event();

        producer.publish(first);
        producer.publish(second);

        verify(kafkaTemplate, times(1)).send(
                DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
                first.dealId().toString(),
                first);

        verify(kafkaTemplate, times(1)).send(
                DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
                second.dealId().toString(),
                second);
    }

    static DealConfirmationDocumentEvent event() {

        UUID dealId = UUID.randomUUID();

        return new DealConfirmationDocumentEvent(
                dealId,
                new DealConfirmationDocumentData(
                        "DC-20260925-000001",
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
