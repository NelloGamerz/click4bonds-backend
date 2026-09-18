package com.click4bonds.app.Config;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MetricsConfig {
    @Bean
    TimedAspect timedAspect(MeterRegistry r) {
        return new TimedAspect(r);
    }

    @Bean
    CountedAspect countedAspect(MeterRegistry r) {
        return new CountedAspect(r);
    }

    @Bean
    ObservedAspect observedAspect(ObservationRegistry r) {
        return new ObservedAspect(r);
    }
}
