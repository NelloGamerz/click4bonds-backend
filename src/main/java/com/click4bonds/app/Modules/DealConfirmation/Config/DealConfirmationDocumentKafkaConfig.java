package com.click4bonds.app.Modules.DealConfirmation.Config;

import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;

import lombok.RequiredArgsConstructor;

/**
 * The topic a created deal publishes its document request to, and the listener
 * container that reads it back.
 *
 * <p>Mirrors {@code Modules.Analytics.Config.KafkaConfig} for the topic itself:
 * a {@link NewTopic} bean so the broker is told the topic exists rather than
 * relying on auto-creation, which the local broker disables.</p>
 *
 * <p><strong>Why this needs a container factory of its own.</strong> Kafka's
 * JSON deserialiser resolves a payload to one fixed class. The shared consumer
 * configuration names {@code AnalyticsEvent}, so a record published here would
 * arrive as an analytics event and be discarded. Rebinding the shared default is
 * not an option — the analytics listener still needs its own type. So the deal
 * listener gets a factory whose deserialiser is pinned to
 * {@link DealConfirmationDocumentEvent}, and the analytics listener keeps the
 * default one.</p>
 *
 * <p><strong>The consumer factory is built inline rather than declared as a
 * bean.</strong> Boot's own {@code ConsumerFactory} backs off when another one
 * exists, which would leave the default {@code kafkaListenerContainerFactory} —
 * and therefore the analytics listener — without the factory it was configured
 * against. Keeping this one a local object means the analytics pipeline is
 * untouched.</p>
 *
 * <p><strong>Its own consumer group.</strong> Sharing
 * {@code click4bonds-analytics} would be legal, since the two listeners read
 * different topics, but it makes lag and rebalancing impossible to attribute to
 * one pipeline. This group reads only this topic.</p>
 */
@Configuration
@RequiredArgsConstructor
public class DealConfirmationDocumentKafkaConfig {

    /**
     * Where a created deal asks for its letter. Named for the thing the message
     * is about rather than the module that owns it, so a second document type
     * can sit beside it without a rename.
     */
    public static final String DOCUMENT_TOPIC =
            "click4bonds.deal-confirmation.document";

    /**
     * The group this application's document listener reads under. Deliberately
     * not the analytics group: the two pipelines have nothing to do with each
     * other and are scaled independently.
     */
    public static final String CONSUMER_GROUP = "click4bonds-deal-documents";

    /**
     * The only package a payload published to this topic may deserialise into.
     *
     * <p>No type headers are written, so nothing on the topic can name a class
     * by itself — this narrows an already-closed door, and is what stops a
     * future change from quietly widening the trust boundary.</p>
     */
    public static final String EVENT_PACKAGE =
            "com.click4bonds.app.Modules.DealConfirmation.Dto";

    private final KafkaProperties kafkaProperties;

    @Bean
    public NewTopic dealConfirmationDocumentTopic() {

        return TopicBuilder
                .name(DOCUMENT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * The factory the deal document listener runs on.
     *
     * <p>Bootstrap servers, security and auto-commit all come from the shared
     * {@code spring.kafka.consumer} configuration, so this cannot drift from the
     * rest of the application; only the payload type and the group are
     * overridden.</p>
     *
     * @param concurrency how many messages this listener renders at once.
     *                    Defaults to three: one more than LibreOffice's default
     *                    conversion permits, so a render waiting on the engine
     *                    does not stop another message's upload progressing.
     *                    Raising it does not raise the number of concurrent
     *                    renderings — that is capped by the converter's own
     *                    semaphore.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DealConfirmationDocumentEvent>
            dealDocumentListenerContainerFactory(
                    @Value("${document.kafka.consumer-concurrency:3}") int concurrency) {

        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();

        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class);

        /*
         * The payload type is named here rather than stamped on the record: the
         * producer writes no type header on purpose, so the topic cannot decide
         * which class it instantiates.
         */
        consumerProperties.put(
                JsonDeserializer.VALUE_DEFAULT_TYPE,
                DealConfirmationDocumentEvent.class.getName());
        consumerProperties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        consumerProperties.put(JsonDeserializer.TRUSTED_PACKAGES, EVENT_PACKAGE);

        var consumerFactory =
                new DefaultKafkaConsumerFactory<String, DealConfirmationDocumentEvent>(
                        consumerProperties);

        var containerFactory =
                new ConcurrentKafkaListenerContainerFactory<String, DealConfirmationDocumentEvent>();

        containerFactory.setConsumerFactory(consumerFactory);
        containerFactory.setConcurrency(concurrency);

        return containerFactory;
    }
}
