package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.click4bonds.app.Config.RedisConfig;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.RedisOperationException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.Common.Redis.RedisServiceImpl;
import com.click4bonds.app.Modules.User.Service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Drives {@link BondService#getBond} through the real {@link RedisServiceImpl}
 * and the real JSON mapper, over an in-memory keyspace.
 *
 * <p>The cache is only worth having if the stored value can be read back, and
 * a value that deserializes into the wrong shape would fail at runtime rather
 * than at build time. So the whole path is exercised here: read through, store,
 * read back, and evict on write.</p>
 */
class BondServiceCacheTest {

    private static final String ISIN = "IN1620170143";
    private static final String CACHE_KEY = "bond:v1:get:" + ISIN;

    /** Ordinary key/value behaviour plus TTL, driven by a movable clock. */
    private final Map<String, Entry> keyspace = new ConcurrentHashMap<>();
    private final AtomicLong clockOffsetSeconds = new AtomicLong();

    private ObjectMapper mapper;
    private BondRepository bondRepository;
    private BondService bondService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {

        mapper = new RedisConfig().redisObjectMapper();

        bondRepository = mock(BondRepository.class);

        when(bondRepository.findByIsin(ISIN)).thenReturn(Optional.of(bond()));
        when(bondRepository.save(any(Bond.class))).thenAnswer(call -> call.getArgument(0));

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(values);

        when(values.get(anyString()))
                .thenAnswer(call -> live(call.getArgument(0)));

        doAnswer(call -> store(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        when(template.hasKey(anyString()))
                .thenAnswer(call -> live(call.getArgument(0)) != null);

        when(template.delete(anyString()))
                .thenAnswer(call -> keyspace.remove(call.getArgument(0)) != null);

        when(template.getExpire(anyString(), any(TimeUnit.class)))
                .thenAnswer(call -> ttl(call.getArgument(0)));

        bondService = new BondService(
                bondRepository,
                mock(UserService.class),
                mock(AnalyticsService.class),
                mock(BondRequestMapper.class),
                new RedisServiceImpl(template, mapper));
    }

    // =========================================================
    // READ-THROUGH
    // =========================================================

    @Test
    void shouldServeTheSecondReadFromRedis() {

        bondService.getBond(ISIN, null, false);
        bondService.getBond(ISIN, null, false);

        verify(bondRepository, times(1)).findByIsin(ISIN);
    }

    @Test
    void shouldMatchACachedBondToTheOriginal() {

        BondResponse first = bondService.getBond(ISIN, null, false);
        BondResponse second = bondService.getBond(ISIN, null, false);

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getIsin(), second.getIsin());
        assertEquals(first.getName(), second.getName());
        assertEquals(0, first.getPrice().compareTo(second.getPrice()));
        assertEquals(BondStatus.ACTIVE, second.getStatus());
    }

    @Test
    void shouldResolveACachedIsinRegardlessOfCase() {

        bondService.getBond(ISIN, null, false);
        bondService.getBond(ISIN.toLowerCase(), null, false);

        verify(bondRepository, times(1)).findByIsin(ISIN);
    }

    // =========================================================
    // SERIALIZATION
    // =========================================================

    @Test
    void shouldStoreTheCachedBondAsReadableJson() {

        bondService.getBond(ISIN, null, false);

        String stored = live(CACHE_KEY);

        assertNotNull(stored, "The bond must have been cached");
        assertTrue(stored.startsWith("{") && stored.endsWith("}"),
                "The entry must be a JSON object, not a JDK stream");
        assertTrue(stored.contains("\"isin\""), "Field names must be readable");
    }

    @Test
    void shouldKeepTheTenMinuteTtl() {

        bondService.getBond(ISIN, null, false);

        long ttl = ttl(CACHE_KEY);

        assertTrue(ttl > 595 && ttl <= 600, "Expected roughly 600 seconds but found " + ttl);
    }

    @Test
    void shouldStopServingAnExpiredBond() {

        bondService.getBond(ISIN, null, false);

        clockOffsetSeconds.addAndGet(Duration.ofMinutes(11).toSeconds());

        bondService.getBond(ISIN, null, false);

        verify(bondRepository, times(2)).findByIsin(ISIN);
    }

    // =========================================================
    // EVICTION
    // =========================================================

    @Test
    void shouldEvictTheCachedBondWhenItIsSuspended() {

        bondService.getBond(ISIN, null, false);

        assertTrue(keyspace.containsKey(CACHE_KEY), "Precondition: the bond is cached");

        bondService.suspendBond(ISIN);

        assertFalse(keyspace.containsKey(CACHE_KEY),
                "Suspending must drop the cached read");
    }

    @Test
    void shouldReReadFromTheDatabaseAfterAnEviction() {

        bondService.getBond(ISIN, null, false);

        bondService.suspendBond(ISIN);

        bondService.getBond(ISIN, null, false);

        verify(bondRepository, times(2)).findByIsin(ISIN);
    }

    // =========================================================
    // FAILURE
    // =========================================================

    @Test
    void shouldFallBackToTheDatabaseWhenRedisIsDown() {

        RedisService unavailable = mock(RedisService.class);

        when(unavailable.get(anyString(), any()))
                .thenThrow(new RedisOperationException("Redis is unreachable"));

        BondService service = new BondService(
                bondRepository,
                mock(UserService.class),
                mock(AnalyticsService.class),
                mock(BondRequestMapper.class),
                unavailable);

        BondResponse bond = service.getBond(ISIN, null, false);

        assertNotNull(bond, "An unreachable cache must not fail the read");
        assertEquals(ISIN, bond.getIsin());
    }

    @Test
    void shouldNotFailTheReadWhenTheCacheWriteFails() {

        RedisService unavailable = mock(RedisService.class);

        when(unavailable.get(anyString(), any())).thenReturn(Optional.empty());
        doAnswer(call -> {
            throw new RedisOperationException("Redis is unreachable");
        }).when(unavailable).set(anyString(), any(), any(Duration.class));

        BondService service = new BondService(
                bondRepository,
                mock(UserService.class),
                mock(AnalyticsService.class),
                mock(BondRequestMapper.class),
                unavailable);

        BondResponse bond = service.getBond(ISIN, null, false);

        assertNotNull(bond, "A failed cache write must not fail the read");
        assertEquals(ISIN, bond.getIsin());
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private static Bond bond() {

        return Bond.builder()
                .id(UUID.fromString("6f6e4c1a-3e0c-4a5e-9c1e-3f3a1c2b4d5e"))
                .serialNumber(1)
                .name("8.45% HAR SDL 2028")
                .isin(ISIN)
                .price(new BigDecimal("102.08"))
                .status(BondStatus.ACTIVE)
                .build();
    }

    // --- in-memory keyspace ---------------------------------------------

    private Instant now() {
        return Instant.now().plusSeconds(clockOffsetSeconds.get());
    }

    private String live(String key) {

        Entry entry = keyspace.get(key);

        if (entry == null) {
            return null;
        }

        if (entry.expiresAt() != null && !now().isBefore(entry.expiresAt())) {
            keyspace.remove(key);
            return null;
        }

        return entry.value();
    }

    private Object store(String key, String value, Duration ttl) {

        keyspace.put(key, new Entry(value, ttl == null ? null : now().plus(ttl)));

        return null;
    }

    private long ttl(String key) {

        Entry entry = keyspace.get(key);

        if (entry == null) {
            return -2L;
        }

        if (entry.expiresAt() == null) {
            return -1L;
        }

        return Math.max(0, Duration.between(now(), entry.expiresAt()).toSeconds());
    }

    private record Entry(String value, Instant expiresAt) {
    }
}
