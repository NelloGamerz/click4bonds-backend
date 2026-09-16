package com.click4bonds.app.Modules.Analytics.Config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ANALYTICS_TOPIC =
            "click4bonds.analytics";

    @Bean
    public NewTopic analyticsTopic() {
        return TopicBuilder
                .name(ANALYTICS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
