package com.click4bonds.app.Modules.Analytics.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import com.click4bonds.app.Modules.Analytics.Config.KafkaConfig;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsBatchService;

/**
 * What {@link AnalyticsEventConsumer} does with a consumed record, and what it
 * is structurally allowed to do.
 *
 * <p>No broker is involved: the listener method is called directly.</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    /** The listener method under test. */
    private static final String LISTENER_METHOD = "onAnalyticsEvent";

    @Mock
    private AnalyticsBatchService analyticsBatchService;

    @Mock
    private Acknowledgment acknowledgment;

    private AnalyticsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AnalyticsEventConsumer(analyticsBatchService);
    }

    @Test
    void shouldHandTheConsumedEventAndItsAcknowledgmentToTheBatchService() {

        AnalyticsEvent event = event();

        consumer.onAnalyticsEvent(event, acknowledgment);

        verify(analyticsBatchService).add(event, acknowledgment);
    }

    @Test
    void shouldPassEachConsumedEventOn() {

        Acknowledgment secondAcknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);

        AnalyticsEvent first = event();
        AnalyticsEvent second = event();

        consumer.onAnalyticsEvent(first, acknowledgment);
        consumer.onAnalyticsEvent(second, secondAcknowledgment);

        verify(analyticsBatchService).add(first, acknowledgment);
        verify(analyticsBatchService).add(second, secondAcknowledgment);
    }

    @Test
    void shouldAcknowledgeANullPayloadRatherThanLeaveItUncommitted() {

        // A record that reaches the listener without a payload would otherwise
        // never be acknowledged, so its offset would never advance and the
        // partition would stall behind it.
        consumer.onAnalyticsEvent(null, acknowledgment);

        verifyNoInteractions(analyticsBatchService);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldListenOnTheTopicTheProducerPublishesTo() throws NoSuchMethodException {

        KafkaListener listener = listenerMethod().getAnnotation(KafkaListener.class);

        assertNotNull(listener, "The listener method must be annotated @KafkaListener");

        assertArrayEquals(
                new String[] {KafkaConfig.ANALYTICS_TOPIC},
                listener.topics(),
                "Producer and consumer must agree on the topic");
    }

    @Test
    void shouldNotNameItsOwnConsumerGroup() throws NoSuchMethodException {

        // Leaving groupId unset means it comes from
        // spring.kafka.consumer.group-id, so the group is configured once.
        KafkaListener listener = listenerMethod().getAnnotation(KafkaListener.class);

        assertEquals("", listener.groupId(),
                "The group id belongs in configuration, not in the annotation");
    }

    @Test
    void shouldDependOnNothingButTheBatchService() {

        Constructor<?>[] constructors = AnalyticsEventConsumer.class.getDeclaredConstructors();

        assertEquals(1, constructors.length, "One constructor, so the wiring is unambiguous");

        assertArrayEquals(
                new Class<?>[] {AnalyticsBatchService.class},
                constructors[0].getParameterTypes(),
                "The consumer must not reach for ClickHouse or Kafka directly");

        for (Field field : AnalyticsEventConsumer.class.getDeclaredFields()) {

            Class<?> type = field.getType();

            assertFalse(DataSource.class.isAssignableFrom(type),
                    "The consumer must not hold a datasource: " + field.getName());

            assertFalse(KafkaTemplate.class.isAssignableFrom(type),
                    "The consumer must not hold a template: " + field.getName());
        }
    }

    @Test
    void shouldTakeTheAcknowledgmentSoTheOffsetWaitsForTheWrite() throws NoSuchMethodException {

        // The listener buffers and returns. Without the Acknowledgment it has no
        // way to hold the offset back until the batch reaches ClickHouse, and
        // the container would commit on return -- the loss this guards against.
        List<? extends Class<?>> parameters = Arrays.stream(listenerMethod().getParameters())
                .map(Parameter::getType)
                .toList();

        assertTrue(parameters.contains(Acknowledgment.class),
                "The listener must accept an Acknowledgment so the batch service can commit it "
                        + "after a successful insert");
    }

    private static Method listenerMethod() throws NoSuchMethodException {
        return AnalyticsEventConsumer.class.getMethod(
                LISTENER_METHOD, AnalyticsEvent.class, Acknowledgment.class);
    }

    private static AnalyticsEvent event() {

        return new AnalyticsEvent(
                UUID.randomUUID(),
                AnalyticsEventType.BOND_VIEW,
                UUID.randomUUID(),
                "session-1",
                UUID.randomUUID(),
                Instant.now(),
                "WEB",
                "BOND_DETAILS",
                Map.of());
    }
}
