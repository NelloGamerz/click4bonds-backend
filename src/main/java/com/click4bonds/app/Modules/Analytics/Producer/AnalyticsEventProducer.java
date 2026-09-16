package com.click4bonds.app.Modules.Analytics.Producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Config.KafkaConfig;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;

import lombok.RequiredArgsConstructor;

/**
 * Publishes analytics events to Kafka.
 *
 * <p>Wraps the {@link KafkaTemplate} so that the topic name and the record key
 * are decided in one place. The key is the event id, which spreads events
 * evenly over the topic's partitions — a constant key would pin every event to
 * one partition and cap the pipeline at a single consumer.</p>
 *
 * <p>{@code send} is asynchronous: it returns once the record is in the
 * producer's buffer. The hand-off to the broker happens on the producer's own
 * I/O thread, so a business request calling
 * {@link com.click4bonds.app.Modules.Analytics.Service.AnalyticsService#track}
 * never blocks on Kafka.</p>
 */
@Service
@RequiredArgsConstructor
public class AnalyticsEventProducer {

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;

    /**
     * Sends one event to {@link KafkaConfig#ANALYTICS_TOPIC}.
     *
     * @param event the event to publish; keyed by its own id
     */
    public void publish(AnalyticsEvent event) {

        kafkaTemplate.send(
                KafkaConfig.ANALYTICS_TOPIC,
                event.eventId().toString(),
                event);
    }
}
