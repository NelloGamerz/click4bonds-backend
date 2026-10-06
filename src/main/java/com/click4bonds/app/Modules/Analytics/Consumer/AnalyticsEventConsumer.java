package com.click4bonds.app.Modules.Analytics.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Analytics.Config.KafkaConfig;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads analytics events off Kafka and hands them to the batch buffer.
 *
 * <p>This class exists to decouple the two halves of the pipeline. The producer
 * side is a web request; the storage side is ClickHouse. Without a consumer in
 * between, everything published to {@value KafkaConfig#ANALYTICS_TOPIC} would
 * accumulate in the topic and never reach a table.</p>
 *
 * <p>It deliberately does nothing else. There is no query building, no
 * ClickHouse call and no business rule here — the listener must stay fast,
 * because the container's poll loop is single threaded and a slow listener means
 * lag on the topic.</p>
 *
 * <p>The consumer group is not named here. It comes from
 * {@code spring.kafka.consumer.group-id}, so the group id is configured in one
 * place and a single deployment cannot accidentally read under two identities.
 * Offsets are not committed by Kafka itself either ({@code enable-auto-commit:
 * false}), and the container is set to {@code MANUAL_IMMEDIATE} — see below.</p>
 *
 * <h2>Why the offset is not committed here</h2>
 *
 * <p>This method only buffers; it does not write anything. Acknowledging on
 * return would therefore commit the event's offset while the event was still in
 * {@link AnalyticsBatchService}'s heap, waiting for the next flush. If the JVM
 * exited in that window the event was gone for good: the offset said it had been
 * handled, so Kafka would never redeliver it, and nothing had ever been written
 * to ClickHouse. That is the loss this signature exists to prevent.</p>
 *
 * <p>The {@link Acknowledgment} is passed to the batch service, which
 * acknowledges it only after the batch containing it has been inserted. A failed
 * insert acknowledges nothing and returns the events to the buffer, so the
 * records stay unconsumed from Kafka's point of view and are redelivered after a
 * restart or rebalance instead of vanishing.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsBatchService analyticsBatchService;

    /**
     * Buffers one consumed event and passes on the means to acknowledge it.
     *
     * <p>Never throws for a ClickHouse problem: a failed insert is handled
     * inside {@code AnalyticsBatchService}, which returns the affected events
     * to the buffer and leaves their offsets uncommitted.</p>
     *
     * <p>A payload that will not deserialize never reaches this method — the
     * container's error handler sends it to the dead-letter topic first. The
     * {@code null} branch below is the guard for a record that arrives without
     * one, and it acknowledges so that such a record cannot stall the
     * partition.</p>
     *
     * @param event          the deserialized event
     * @param acknowledgment the container's handle for this record's offset
     */
    @KafkaListener(topics = KafkaConfig.ANALYTICS_TOPIC)
    public void onAnalyticsEvent(AnalyticsEvent event, Acknowledgment acknowledgment) {

        if (event == null) {
            log.warn("Discarded a null analytics payload from {}", KafkaConfig.ANALYTICS_TOPIC);
            acknowledgment.acknowledge();
            return;
        }

        analyticsBatchService.add(event, acknowledgment);
    }
}
