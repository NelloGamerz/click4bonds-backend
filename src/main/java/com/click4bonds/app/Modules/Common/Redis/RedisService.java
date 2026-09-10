package com.click4bonds.app.Modules.Common.Redis;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin, reusable abstraction over Redis.
 *
 * <p>Modules talk to this interface instead of {@code RedisTemplate}. That
 * keeps Redis an implementation detail the application can swap, stub or
 * test, and gives every module the same key/value semantics.</p>
 *
 * <p>This type is intentionally generic. Domain-specific helpers (an OTP
 * module's {@code saveOtp}/{@code verifyOtp}, a cache's {@code getOrLoad},
 * ...) belong to those modules, not here.</p>
 */
public interface RedisService {

    /**
     * Stores {@code value} under {@code key}.
     *
     * @param ttl time to live; when {@code null}, zero or negative the entry is
     *            stored without expiry
     */
    <T> void set(String key, T value, Duration ttl);

    /**
     * Reads the value stored under {@code key}.
     *
     * @param type expected value type
     * @return the value, or {@link Optional#empty()} when the key is absent
     *         (or has expired)
     * @throws com.click4bonds.app.Modules.Common.Exceptions.RedisOperationException
     *         if the stored value is not of the requested type
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Removes {@code key}. Removing a missing key is not an error.
     */
    void delete(String key);

    /**
     * @return {@code true} when {@code key} currently exists
     */
    boolean exists(String key);

    /**
     * Remaining time to live of {@code key}, in seconds, following Redis
     * semantics: {@code -1} when the key exists without expiry, {@code -2}
     * when the key does not exist.
     */
    long getTtl(String key);

    /**
     * Atomically stores {@code value} under {@code key} only if the key is
     * currently absent, together with its {@code ttl}.
     *
     * <p>This is the primitive behind cooldowns and single-flight guards:
     * exactly one caller can win the race without any distributed lock.</p>
     *
     * @return {@code true} when this call created the key
     */
    boolean setIfAbsent(String key, Object value, Duration ttl);
}
