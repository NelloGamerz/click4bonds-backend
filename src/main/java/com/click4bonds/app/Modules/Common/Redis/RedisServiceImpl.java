package com.click4bonds.app.Modules.Common.Redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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

    /**
     * Applies an {@link #applyAtomically} batch inside Redis, so the whole set
     * takes effect at once or not at all.
     *
     * <p>Sent as one command, which is the point: three separate calls would be
     * observably half-applied between the first and the last.</p>
     *
     * <p>Argument layout — {@code KEYS} holds the write keys followed by the
     * delete keys; {@code ARGV} holds the write count, then a TTL in
     * milliseconds per write ({@code ''} for none), then the values. Deletions
     * need no argument, so they are addressed by position in {@code KEYS}
     * alone.</p>
     */
    private static final RedisScript<Long> ATOMIC_MUTATION = new DefaultRedisScript<>("""
            local writes = tonumber(ARGV[1])
            for i = 1, writes do
              local ttl = ARGV[1 + i]
              local value = ARGV[1 + writes + i]
              if ttl == '' then
                redis.call('SET', KEYS[i], value)
              else
                redis.call('SET', KEYS[i], value, 'PX', ttl)
              end
            end
            for i = writes + 1, #KEYS do
              redis.call('DEL', KEYS[i])
            end
            return 1
            """, Long.class);

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

    @Override
    public long increment(String key, Duration ttl) {

        try {
            Long count = redisTemplate.opsForValue().increment(key);

            if (count == null) {
                throw new RedisOperationException("Redis returned no counter value");
            }

            // Only the call that created the counter arms the window. Doing it
            // on every increment would turn a fixed window into a sliding one
            // that never expires while the caller stays active.
            if (count == 1L && hasExpiry(ttl)) {
                redisTemplate.expire(key, ttl);
            }

            return count;

        } catch (RedisOperationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not increment a Redis counter", ex);
        }
    }

    @Override
    public void applyAtomically(List<Write> writes, List<String> deletes) {

        if (writes.isEmpty() && deletes.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>(writes.size() + deletes.size());
        List<String> args = new ArrayList<>(1 + writes.size() * 2);

        args.add(Integer.toString(writes.size()));

        // TTLs first, then values: the script indexes both by write position,
        // so the two runs have to stay in that order.
        for (Write write : writes) {
            keys.add(write.key());

            Duration ttl = write.ttl();
            args.add(hasExpiry(ttl) ? Long.toString(ttl.toMillis()) : "");
        }

        for (Write write : writes) {
            args.add(write(write.value()));
        }

        for (String key : deletes) {
            keys.add(key);
        }

        try {
            redisTemplate.execute(ATOMIC_MUTATION, keys, args.toArray(new String[0]));

        } catch (RuntimeException ex) {
            throw new RedisOperationException("Could not apply an atomic Redis mutation", ex);
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
