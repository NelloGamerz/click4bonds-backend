package com.click4bonds.app.Modules.Analytics.Config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;

/**
 * What {@link KafkaConfig} does with a record the consumer cannot read.
 *
 * <p>No broker: the recoverer is driven directly with the headers
 * {@code ErrorHandlingDeserializer} leaves behind, and the dead-letter
 * publication is captured from a mocked template. That is enough to pin the two
 * things that decide whether one bad record halts the pipeline — the topic it
 * goes to, and whether the bytes that failed are the bytes that are republished.
 * </p>
 */
class KafkaConfigTest {

    @Test
    void shouldSendAnUnreadableRecordToTheDeadLetterTopicWithItsOriginalBytes() {

        KafkaTemplate<String, byte[]> template = deadLetterTemplateMock();

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(template, KafkaConfig.DEAD_LETTER_DESTINATION);

        byte[] malformed = "{ \"eventType\": ".getBytes(StandardCharsets.UTF_8);

        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>(KafkaConfig.ANALYTICS_TOPIC, 2, 17L, "some-key", null);

        // Exactly what ErrorHandlingDeserializer adds to a record whose value it
        // could not parse: the value becomes null and the original data and
        // cause travel in a header.
        DeserializationException failure = new DeserializationException(
                "Could not deserialize", malformed, false, new RuntimeException("not json"));

        SerializationUtils.deserializationException(record.headers(), malformed, failure, false);

        recoverer.accept(record, failure);

        ProducerRecord<String, byte[]> published = captureDeadLetter(template);

        assertEquals(KafkaConfig.ANALYTICS_DEAD_LETTER_TOPIC, published.topic(),
                "The record must go to the topic this application creates");
        assertEquals(2, published.partition(), "The record keeps the partition it came from");
        assertArrayEquals(malformed, published.value(),
                "The dead letter must carry the bytes that failed, unparsed");
    }

    @Test
    void shouldCreateTheDeadLetterTopicUnderTheNameTheRecovererPublishesTo() {

        KafkaConfig config = new KafkaConfig();

        assertEquals(KafkaConfig.ANALYTICS_DEAD_LETTER_TOPIC,
                config.analyticsDeadLetterTopic().name(),
                "A publish to a topic that was never created would fail, and a failed "
                        + "dead-letter publish leaves the record blocking the partition");

        assertEquals(config.analyticsTopic().numPartitions(),
                config.analyticsDeadLetterTopic().numPartitions(),
                "Carrying the source partition over needs the dead letter topic to have "
                        + "at least as many partitions");
    }

    @Test
    void shouldSerializeDeadLetterValuesAsRawBytes() {

        // Anything else would re-encode a payload that had already failed to
        // parse, which is the one thing the dead letter must not do.
        KafkaTemplate<String, byte[]> template =
                KafkaConfig.deadLetterTemplate(new HashMap<>());

        Map<String, Object> properties = template.getProducerFactory().getConfigurationProperties();

        assertEquals(ByteArraySerializer.class,
                properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals(StringSerializer.class,
                properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
    }

    @Test
    void shouldAcknowledgeARecoveredRecordSoThePartitionResumes() {

        DefaultErrorHandler handler = new KafkaConfig().analyticsErrorHandler(
                new DeadLetterPublishingRecoverer(
                        deadLetterTemplateMock(), KafkaConfig.DEAD_LETTER_DESTINATION));

        // With auto-commit off, the container commits a recovered record's offset
        // only when the handler reports this. Without it the same record would be
        // polled again forever and the partition would never move.
        assertTrue(handler.isAckAfterHandle(),
                "A recovered record must have its offset committed, or it is re-polled forever");
    }

    /** @return a template whose sends complete so the recoverer does not block */
    private static KafkaTemplate<String, byte[]> deadLetterTemplateMock() {

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);

        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        return template;
    }

    /** @return the record the recoverer handed to the template */
    private static ProducerRecord<String, byte[]> captureDeadLetter(
            KafkaTemplate<String, byte[]> template) {

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        verify(template).send(captor.capture());

        return captor.getValue();
    }
}
