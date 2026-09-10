package com.click4bonds.app.Modules.Common.Redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Config.RedisConfig;
import com.click4bonds.app.Modules.Common.Exceptions.RedisOperationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default {@link RedisService}: values are stored as JSON strings.
 *
 * <p>The caller always names the type it expects, and the value is bound to
 * that type on the way out. Nothing about the payload decides which class is
 * instantiated, so a value stays readable no matter which classloader happens
 * to be current when it is read — the failure that JDK native serialization
 * produced, where an object came back as a look-alike class with an identical
 * name that failed every identity check.</p>
 *
 * <p>Keys stay plain strings, so the store remains inspectable.</p>
 */
@Service
public class RedisServiceImpl implements RedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisServiceImpl(
            StringRedisTemplate redisTemplate,
            @Qualifier(RedisConfig.REDIS_OBJECT_MAPPER) ObjectMapper objectMapper) {

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> void set(String key, T value, Duration ttl) {

        String json = write(value);

        try {
            if (hasExpiry(ttl)) {
                redisTemplate.opsForValue().set(key, json, ttl);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not write to Redis", ex);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {

        String json;

        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not read from Redis", ex);
        }

        if (json == null) {
            return Optional.empty();
        }

        try {
            // Binding straight to the requested type is what makes the result
            // an instance of it by construction: no identity check can fail.
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException ex) {
            // Never echo the stored payload: it can hold PII or secrets.
            throw new RedisOperationException(
                    "Redis value could not be read as " + type.getSimpleName(), ex);
        }
    }

    @Override
    public void delete(String key) {

        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not delete from Redis", ex);
        }
    }

    @Override
    public boolean exists(String key) {

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not check Redis key existence", ex);
        }
    }

    @Override
    public long getTtl(String key) {

        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl == null ? -2L : ttl;
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not read Redis key TTL", ex);
        }
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {

        String json = write(value);

        try {
            Boolean created = hasExpiry(ttl)
                    ? redisTemplate.opsForValue().setIfAbsent(key, json, ttl)
                    : redisTemplate.opsForValue().setIfAbsent(key, json);

            return Boolean.TRUE.equals(created);
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not write to Redis", ex);
        }
    }

    private String write(Object value) {

        if (value == null) {
            throw new RedisOperationException("Cannot store a null value in Redis");
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new RedisOperationException(
                    "Could not serialize a " + value.getClass().getSimpleName() + " for Redis", ex);
        }
    }

    private boolean hasExpiry(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
