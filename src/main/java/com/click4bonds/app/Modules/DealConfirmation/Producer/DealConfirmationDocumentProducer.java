package com.click4bonds.app.Modules.DealConfirmation.Producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentKafkaConfig;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes a created deal's request for its confirmation letter.
 *
 * <p>Follows {@code AnalyticsEventProducer}: the topic and the record key are
 * decided in one place. The key is the deal id, so every message about one deal
 * lands on the same partition and therefore in the order it was sent — which
 * matters here, because the same deal can legitimately be regenerated later and
 * a re-render must not overtake the original. It also spreads unrelated deals
 * across the topic's partitions, where a constant key would pin them all to one
 * consumer.</p>
 *
 * <p><strong>{@code send} is asynchronous.</strong> It returns as soon as the
 * record is in the producer's buffer, so the HTTP request that created the deal
 * does not wait for a broker round trip — and certainly not for the letter
 * itself, which is rendered on the other side of the topic. A broker that is
 * down therefore does not fail the purchase; it surfaces in the completion
 * callback below and in the caller's own guard.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationDocumentProducer {

    private final KafkaTemplate<String, DealConfirmationDocumentEvent> kafkaTemplate;

    /**
     * Sends one document request to
     * {@link DealConfirmationDocumentKafkaConfig#DOCUMENT_TOPIC}.
     *
     * @param event the request to publish; keyed by the deal it belongs to
     */
    public void publish(DealConfirmationDocumentEvent event) {

        kafkaTemplate.send(
                        DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
                        event.dealId().toString(),
                        event)
                .whenComplete((result, exception) -> {

                    if (exception != null) {

                        log.error(
                                "Failed to publish the document request for deal {} to Kafka topic {}",
                                event.documentData().dealReference(),
                                DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
                                exception);

                        return;
                    }

                    log.info(
                            "Published the document request for deal {} to Kafka topic {} partition={} offset={}",
                            event.documentData().dealReference(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }
}
