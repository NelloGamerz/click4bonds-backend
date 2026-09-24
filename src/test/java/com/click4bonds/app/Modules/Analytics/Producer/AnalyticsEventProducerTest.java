package com.click4bonds.app.Modules.Analytics.Producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.click4bonds.app.Modules.Analytics.Config.KafkaConfig;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;

/**
 * What {@link AnalyticsEventProducer} puts on the wire.
 *
 * <p>The template is mocked, so no broker is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsEventProducerTest {

    @Mock
    private KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;

    private AnalyticsEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new AnalyticsEventProducer(kafkaTemplate);
    }

    @Test
    void shouldPublishToTheAnalyticsTopicKeyedByEventId() {

        AnalyticsEvent event = event(AnalyticsEventType.BOND_VIEW);

        producer.publish(event);

        verify(kafkaTemplate).send(
                KafkaConfig.ANALYTICS_TOPIC,
                event.eventId().toString(),
                event);
    }

    @Test
    void shouldPublishToTheTopicTheConsumerListensOn() {

        // Guards the pairing: changing one side without the other would send
        // every event to a topic nobody reads.
        assertEquals("click4bonds.analytics", KafkaConfig.ANALYTICS_TOPIC);
    }

    @Test
    void shouldUseEachEventsOwnIdAsTheKey() {

        AnalyticsEvent first = event(AnalyticsEventType.LOGIN);
        AnalyticsEvent second = event(AnalyticsEventType.LOGOUT);

        producer.publish(first);
        producer.publish(second);

        verify(kafkaTemplate, times(1)).send(
                KafkaConfig.ANALYTICS_TOPIC, first.eventId().toString(), first);

        verify(kafkaTemplate, times(1)).send(
                KafkaConfig.ANALYTICS_TOPIC, second.eventId().toString(), second);
    }

    private static AnalyticsEvent event(AnalyticsEventType type) {

        return new AnalyticsEvent(
                UUID.randomUUID(),
                type,
                UUID.randomUUID(),
                "session-1",
                UUID.randomUUID(),
                Instant.now(),
                "WEB",
                "BOND_DETAILS",
                Map.of());
    }
}
