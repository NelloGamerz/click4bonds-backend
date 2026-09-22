package com.click4bonds.app.Modules.Common.Redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link RedisService} for tests that need real key/value behaviour
 * — TTLs, atomic {@code setIfAbsent} — without a running Redis.
 *
 * <p>Time is driven by {@link #advance(Duration)} rather than the wall clock
 * so expiry and cooldown can be tested without sleeping.</p>
 */
public class InMemoryRedisService implements RedisService {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong offsetSeconds = new AtomicLong();

    /**
     * Moves this fake's clock forward, expiring anything whose TTL has
     * elapsed.
     */
    public void advance(Duration amount) {
        offsetSeconds.addAndGet(amount.toSeconds());
    }

    @Override
    public <T> void set(String key, T value, Duration ttl) {
        store.put(key, new Entry(value, expiryFrom(ttl)));
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {

        Entry entry = liveEntry(key);

        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(type.cast(entry.value()));
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return liveEntry(key) != null;
    }

    @Override
    public long getTtl(String key) {

        Entry entry = liveEntry(key);

        if (entry == null) {
            return -2;
        }

        if (entry.expiresAt() == null) {
            return -1;
        }

        return Math.max(0, Duration.between(now(), entry.expiresAt()).toSeconds());
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {

        boolean[] created = { false };

        store.compute(key, (k, existing) -> {

            if (existing != null && !isExpired(existing)) {
                return existing;
            }

            created[0] = true;
            return new Entry(value, expiryFrom(ttl));
        });

        return created[0];
    }

    @Override
    public long increment(String key, Duration ttl) {

        long[] result = { 0 };

        store.compute(key, (k, existing) -> {

            // An expired counter is indistinguishable from an absent one, so
            // this call opens a fresh window — matching Redis, where the key
            // would already be gone.
            if (existing == null || isExpired(existing)) {
                result[0] = 1;
                return new Entry(1L, expiryFrom(ttl));
            }

            long next = ((Number) existing.value()).longValue() + 1;
            result[0] = next;

            // The window is armed on creation and never pushed forward.
            return new Entry(next, existing.expiresAt());
        });

        return result[0];
    }

    private Entry liveEntry(String key) {

        Entry entry = store.get(key);

        if (entry == null) {
            return null;
        }

        if (isExpired(entry)) {
            store.remove(key);
            return null;
        }

        return entry;
    }

    private boolean isExpired(Entry entry) {
        return entry.expiresAt() != null && !now().isBefore(entry.expiresAt());
    }

    private Instant expiryFrom(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative()
                ? null
                : now().plus(ttl);
    }

    private Instant now() {
        return Instant.now().plusSeconds(offsetSeconds.get());
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}
