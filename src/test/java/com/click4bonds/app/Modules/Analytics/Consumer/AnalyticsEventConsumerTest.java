package com.click4bonds.app.Modules.Analytics.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private AnalyticsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AnalyticsEventConsumer(analyticsBatchService);
    }

    @Test
    void shouldHandTheConsumedEventToTheBatchService() {

        AnalyticsEvent event = event();

        consumer.onAnalyticsEvent(event);

        verify(analyticsBatchService).add(event);
    }

    @Test
    void shouldPassEachConsumedEventOn() {

        AnalyticsEvent first = event();
        AnalyticsEvent second = event();

        consumer.onAnalyticsEvent(first);
        consumer.onAnalyticsEvent(second);

        verify(analyticsBatchService).add(first);
        verify(analyticsBatchService).add(second);
    }

    @Test
    void shouldIgnoreANullPayload() {

        consumer.onAnalyticsEvent(null);

        verifyNoInteractions(analyticsBatchService);
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
    void shouldLetTheContainerManageOffsets() throws NoSuchMethodException {

        // No Acknowledgment parameter, and the container's ack mode is left at
        // its default, so the offset is committed once this method returns.
        List<? extends Class<?>> parameters = Arrays.stream(listenerMethod().getParameters())
                .map(Parameter::getType)
                .toList();

        assertFalse(parameters.contains(Acknowledgment.class),
                "Manual acknowledgement would need an explicit ack-mode setting");
    }

    private static Method listenerMethod() throws NoSuchMethodException {
        return AnalyticsEventConsumer.class.getMethod(LISTENER_METHOD, AnalyticsEvent.class);
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
