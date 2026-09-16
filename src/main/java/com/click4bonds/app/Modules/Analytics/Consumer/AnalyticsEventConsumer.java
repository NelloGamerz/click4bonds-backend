package com.click4bonds.app.Modules.Analytics.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
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
 * because the container commits the offset for a record as soon as this method
 * returns, and slow listeners mean lag on the topic.</p>
 *
 * <p>The consumer group is not named here. It comes from
 * {@code spring.kafka.consumer.group-id}, so the group id is configured in one
 * place and a single deployment cannot accidentally read under two identities.
 * Offsets are not committed by Kafka itself either ({@code enable-auto-commit:
 * false}); the container commits them after this method returns.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsBatchService analyticsBatchService;

    /**
     * Buffers one consumed event.
     *
     * <p>Never throws for a ClickHouse problem: a failed insert is handled
     * inside {@code AnalyticsBatchService}, which returns the affected events
     * to the buffer. Letting the exception escape instead would have the
     * container redeliver records whose only real problem is that the storage
     * is down, and would eventually skip them.</p>
     *
     * @param event the deserialized event; a {@code null} payload is ignored
     */
    @KafkaListener(topics = KafkaConfig.ANALYTICS_TOPIC)
    public void onAnalyticsEvent(AnalyticsEvent event) {

        if (event == null) {
            log.warn("Discarded a null analytics payload from {}", KafkaConfig.ANALYTICS_TOPIC);
            return;
        }

        analyticsBatchService.add(event);
    }
}
