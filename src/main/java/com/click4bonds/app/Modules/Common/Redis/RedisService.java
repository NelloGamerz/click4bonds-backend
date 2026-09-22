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

    /**
     * Atomically increments the counter under {@code key} and returns its new
     * value, applying {@code ttl} when the counter is created.
     *
     * <p>This is the primitive behind "at most N times per window": counting
     * has to be atomic, because a read-then-write would let two concurrent
     * callers both observe the same count and each decide they were the last
     * one allowed.</p>
     *
     * <p>The TTL is set only on creation, so a window is fixed from the first
     * event rather than pushed forward by every subsequent one. A caller that
     * wants a sliding window would have to re-arm it explicitly.</p>
     *
     * @param ttl window length; when {@code null}, zero or negative the counter
     *            is created without expiry
     * @return the counter's value after incrementing; it is never zero, so a
     *         return of {@code 1} means this call opened the window
     */
    long increment(String key, Duration ttl);
}
