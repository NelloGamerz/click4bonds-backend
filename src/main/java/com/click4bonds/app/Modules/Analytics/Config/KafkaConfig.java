package com.click4bonds.app.Modules.Analytics.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * The analytics topic, its dead-letter topic, and what happens to a record the
 * consumer cannot handle.
 *
 * <p>Without the error handler below, a record whose payload will not
 * deserialize is not skipped: the container re-polls it on every iteration and
 * never advances the offset, so the partition — and with it a share of all
 * analytics traffic — stops dead behind one bad record, logging the failure in
 * a hot loop. Routing it to the DLT is what keeps the rest of the topic moving.
 * </p>
 */
@Configuration
public class KafkaConfig {

    public static final String ANALYTICS_TOPIC =
            "click4bonds.analytics";

    /**
     * Where {@link DeadLetterPublishingRecoverer} parks records it cannot
     * handle.
     *
     * <p>The {@code -dlt} suffix matches the recoverer's own default destination
     * resolver, but the bean does not rely on that coincidence: Spring Kafka has
     * used {@code .DLT} in earlier versions, so the resolver is passed
     * explicitly below and this constant is what it resolves to. Deriving the
     * name here rather than writing it twice keeps the topic that is created and
     * the topic that is published to from ever disagreeing — with
     * {@code KAFKA_AUTO_CREATE_TOPICS_ENABLE: false}, a publish to a topic that
     * was never created fails, and a failed dead-letter publish leaves the
     * original record blocking the partition, which is the very thing this
     * handler exists to prevent.</p>
     */
    public static final String ANALYTICS_DEAD_LETTER_TOPIC =
            ANALYTICS_TOPIC + "-dlt";

    /**
     * Where a failed record goes: {@link #ANALYTICS_DEAD_LETTER_TOPIC}, on the
     * partition it came from.
     *
     * <p>Passed to the recoverer explicitly rather than left to its default.
     * Spring Kafka's default destination appends a suffix to the source topic
     * name — and that suffix has changed between versions — so deriving both the
     * created topic and the published topic from one constant is what keeps them
     * from drifting apart.</p>
     */
    static final BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> DEAD_LETTER_DESTINATION =
            (record, exception) -> new TopicPartition(ANALYTICS_DEAD_LETTER_TOPIC, record.partition());

    @Bean
    public NewTopic analyticsTopic() {
        return TopicBuilder
                .name(ANALYTICS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic analyticsDeadLetterTopic() {
        return TopicBuilder
                .name(ANALYTICS_DEAD_LETTER_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Publishes records the listener cannot process to
     * {@link #ANALYTICS_DEAD_LETTER_TOPIC}.
     *
     * <p>The template it publishes through is deliberately not the application's
     * {@code KafkaTemplate<String, AnalyticsEvent>}. A record that failed to
     * deserialize carries its original bytes in a header, not in a typed
     * payload, and the recoverer republishes those bytes verbatim — which only
     * survives a {@link ByteArraySerializer}. Routing them through the JSON
     * serializer would base64 the payload and leave the dead letter unreadable.
     * </p>
     *
     * <p>The template is built here rather than declared as a bean on purpose.
     * Boot's own {@code KafkaTemplate} backs off on
     * {@code @ConditionalOnMissingBean(KafkaTemplate.class)}, which matches on
     * the raw type, so a second template bean would quietly remove the one the
     * producer injects.</p>
     *
     * @param kafkaProperties the same producer settings the application uses, so
     *                        bootstrap servers and security are not configured
     *                        twice
     * @return the recoverer the error handler delegates to
     */
    @Bean
    public DeadLetterPublishingRecoverer analyticsDeadLetterRecoverer(
            KafkaProperties kafkaProperties) {

        KafkaTemplate<String, byte[]> template =
                deadLetterTemplate(kafkaProperties.buildProducerProperties());

        // The same constant the topic is created under, so the two cannot
        // disagree. The partition is carried over so a partition's records keep
        // their relative order in the dead letter topic, which is why that topic
        // is created with the same partition count as the source.
        return new DeadLetterPublishingRecoverer(template, DEAD_LETTER_DESTINATION);
    }

    /**
     * A producer for dead-letter records, serializing values as raw bytes.
     *
     * <p>Package-private rather than a bean: Boot's own {@code KafkaTemplate}
     * backs off on {@code @ConditionalOnMissingBean(KafkaTemplate.class)}, which
     * matches on the raw type, so a second template bean would quietly remove
     * the one {@code AnalyticsEventProducer} injects.</p>
     *
     * @param producerProperties the application's producer settings
     * @return a template that republishes a failed record's bytes untouched
     */
    static KafkaTemplate<String, byte[]> deadLetterTemplate(Map<String, Object> producerProperties) {

        Map<String, Object> properties = new HashMap<>(producerProperties);

        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    /**
     * The container's error handler: hand the record to the dead-letter
     * recoverer immediately, with no retry.
     *
     * <p>A {@link FixedBackOff} of zero retries is the point. The failures that
     * reach here are malformed payloads — retrying the same bytes cannot change
     * the outcome, and each attempt would stall the partition for another
     * interval. Business failures do not arrive here at all: the listener
     * buffers and never throws, and a ClickHouse outage is absorbed by
     * {@code AnalyticsBatchService} instead.</p>
     *
     * <p>Defining this bean is also what stops the auto-configured listener
     * container from having no error handler at all: Boot passes any
     * {@code CommonErrorHandler} bean into the container factory.</p>
     *
     * @param analyticsDeadLetterRecoverer where unrecoverable records are sent
     * @return the error handler wired into the listener container
     */
    @Bean
    public DefaultErrorHandler analyticsErrorHandler(
            DeadLetterPublishingRecoverer analyticsDeadLetterRecoverer) {

        return new DefaultErrorHandler(analyticsDeadLetterRecoverer, new FixedBackOff(0L, 0L));
    }
}
