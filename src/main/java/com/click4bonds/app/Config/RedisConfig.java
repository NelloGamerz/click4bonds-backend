package com.click4bonds.app.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serialization strategy for values stored in Redis.
 *
 * <p>Values are plain JSON. They used to be JDK-serialized, which tied every
 * stored object to the exact {@code Class} instance that wrote it: a JDK stream
 * stores only the class <em>name</em>, so reading it back through a different
 * classloader — which Spring Boot DevTools' restart classloader produces
 * routinely — yields a look-alike class that fails every identity check while
 * printing the same name. JSON carries field names and values instead, so the
 * payload stays readable by any classloader and can be inspected from
 * {@code redis-cli}.</p>
 *
 * <p>The mapper is deliberately its own bean rather than the application's
 * shared one. Redis holds persisted data whose format must not shift because
 * somebody retuned {@code spring.jackson.*} for the web layer.</p>
 *
 * <p>No polymorphic type information is written or honoured: a reader always
 * says which type it expects, so stored data can never dictate which class gets
 * instantiated. That rules out the deserialization-gadget class of attack that
 * JDK native serialization is notorious for.</p>
 */
@Configuration
public class RedisConfig {

    /** Bean name of the mapper used for Redis values. */
    public static final String REDIS_OBJECT_MAPPER = "redisObjectMapper";

    /**
     * @return mapper that renders Redis values as portable JSON
     */
    @Bean(REDIS_OBJECT_MAPPER)
    public ObjectMapper redisObjectMapper() {

        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                // Readable, zone-explicit timestamps rather than epoch numbers.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // A field that disappears from a value object must not make
                // every already-stored entry unreadable.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
