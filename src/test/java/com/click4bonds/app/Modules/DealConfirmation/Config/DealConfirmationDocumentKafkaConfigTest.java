package com.click4bonds.app.Modules.DealConfirmation.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.click4bonds.app.Modules.Analytics.Config.KafkaConfig;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;

/**
 * The topic, and the container the deal document listener reads it with.
 *
 * <p>No broker is involved. The configuration is built against default
 * properties, which is enough to prove that the payload type is pinned and that
 * the consumer factory is not registered as a bean — the two things that would
 * otherwise break the analytics pipeline silently.</p>
 */
class DealConfirmationDocumentKafkaConfigTest {

    private final KafkaProperties kafkaProperties = new KafkaProperties();

    private final DealConfirmationDocumentKafkaConfig config =
            new DealConfirmationDocumentKafkaConfig(kafkaProperties);

    @Test
    void declaresTheTopicTheProducerAndConsumerBothName() {

        NewTopic topic = config.dealConfirmationDocumentTopic();

        assertEquals("click4bonds.deal-confirmation.document", topic.name());
        assertEquals(3, topic.numPartitions());
    }

    @Test
    void readsUnderItsOwnConsumerGroup() {

        /*
         * Deliberately not the analytics group. Sharing it would be legal — the
         * two listeners read different topics — but it makes lag and rebalancing
         * impossible to attribute to one pipeline.
         */
        assertNotEquals(
                "click4bonds-analytics",
                DealConfirmationDocumentKafkaConfig.CONSUMER_GROUP,
                "The document pipeline must not read under the analytics group");

        assertEquals(
                "click4bonds-deal-documents",
                DealConfirmationDocumentKafkaConfig.CONSUMER_GROUP);
    }

    @Test
    void doesNotReuseTheAnalyticsTopic() {

        assertNotEquals(
                KafkaConfig.ANALYTICS_TOPIC,
                DealConfirmationDocumentKafkaConfig.DOCUMENT_TOPIC,
                "A deal's letter request must not be mixed into the analytics stream");
    }

    @Test
    void pinsTheDeserialiserToTheDealPayloadType() {

        /*
         * The load-bearing assertion. Kafka's JSON deserialiser resolves a
         * payload to one fixed class, and the shared consumer configuration names
         * the analytics event. Without this override a deal's request would be
         * deserialised as the wrong type and discarded — with the application
         * still starting and every deal silently left unlettered.
         */
        ConcurrentKafkaListenerContainerFactory<String, DealConfirmationDocumentEvent> factory =
                config.dealDocumentListenerContainerFactory(3);

        assertNotNull(factory.getConsumerFactory());

        var consumerFactory =
                (DefaultKafkaConsumerFactory<?, ?>) factory.getConsumerFactory();

        assertEquals(
                DealConfirmationDocumentEvent.class.getName(),
                consumerFactory.getConfigurationProperties()
                        .get(JsonDeserializer.VALUE_DEFAULT_TYPE));

        assertEquals(
                Boolean.FALSE,
                consumerFactory.getConfigurationProperties()
                        .get(JsonDeserializer.USE_TYPE_INFO_HEADERS),
                "The topic must not be able to name the class it deserialises into");

        assertEquals(
                DealConfirmationDocumentKafkaConfig.CONSUMER_GROUP,
                consumerFactory.getConfigurationProperties()
                        .get(ConsumerConfig.GROUP_ID_CONFIG));
    }

    @Test
    void takesItsBrokerSettingsFromTheSharedConfiguration() {

        /*
         * Only the payload type and the group are overridden. Bootstrap servers,
         * security and ack behaviour stay in spring.kafka.consumer, so this
         * pipeline cannot quietly point at a different broker from the rest of
         * the application.
         */
        kafkaProperties.setBootstrapServers(java.util.List.of("broker.internal:9092"));

        ConcurrentKafkaListenerContainerFactory<String, DealConfirmationDocumentEvent> factory =
                config.dealDocumentListenerContainerFactory(3);

        Object servers =
                ((DefaultKafkaConsumerFactory<?, ?>) factory.getConsumerFactory())
                        .getConfigurationProperties()
                        .get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);

        assertNotNull(servers, "The shared bootstrap servers must reach this factory");

        assertTrue(
                servers.toString().contains("broker.internal:9092"),
                "expected the shared bootstrap servers, got " + servers);
    }
}
