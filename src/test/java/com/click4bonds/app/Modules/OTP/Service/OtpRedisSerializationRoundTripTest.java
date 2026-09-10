package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.click4bonds.app.Config.RedisConfig;
import com.click4bonds.app.Modules.Common.Redis.RedisServiceImpl;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.testing.ForeignOtpData;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Drives {@link OtpService} through the real {@link RedisServiceImpl} and the
 * real JSON mapper, over an in-memory keyspace.
 *
 * <p>The other OTP tests substitute {@code RedisService} wholesale, so they
 * never touch serialization. That gap is exactly where a stored value could
 * come back as something the reader did not expect, so the whole path is
 * exercised here: generate, store, read back, verify.</p>
 */
class OtpRedisSerializationRoundTripTest {

    private static final String EMAIL = OtpTestSupport.EMAIL;
    private static final String OTP = "483920";

    /** Ordinary key/value behaviour plus TTL, driven by a movable clock. */
    private final Map<String, Entry> keyspace = new ConcurrentHashMap<>();
    private final AtomicLong clockOffsetSeconds = new AtomicLong();

    private ObjectMapper mapper;
    private OtpProperties properties;
    private OtpService otpService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {

        mapper = new RedisConfig().redisObjectMapper();
        properties = OtpTestSupport.defaultProperties();

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(values);

        when(values.get(anyString()))
                .thenAnswer(call -> live(call.getArgument(0)));

        doAnswer(call -> store(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(call -> storeIfAbsent(call.getArgument(0), call.getArgument(1), call.getArgument(2)));

        when(template.hasKey(anyString()))
                .thenAnswer(call -> live(call.getArgument(0)) != null);

        when(template.delete(anyString()))
                .thenAnswer(call -> keyspace.remove(call.getArgument(0)) != null);

        when(template.getExpire(anyString(), any(java.util.concurrent.TimeUnit.class)))
                .thenAnswer(call -> ttl(call.getArgument(0)));

        otpService = new OtpService(
                new RedisServiceImpl(template, mapper),
                OtpTestSupport.fixedGenerator(OTP),
                new OtpHasher(properties),
                properties);
    }

    private static String key() {
        return OtpKeyFactory.otpKey(OtpType.EMAIL, EMAIL);
    }

    private String rawStored() {
        return live(key());
    }

    @Test
    void shouldStoreThePendingOtpAsReadableJson() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        String stored = rawStored();

        assertTrue(stored.startsWith("{") && stored.endsWith("}"),
                "The entry must be a JSON object, not a JDK stream");
        assertTrue(stored.contains("otpType"), "Field names must be readable");
        assertFalse(stored.contains(OTP),
                "The plaintext code must never reach Redis");
    }

    @Test
    void shouldVerifyAnOtpThroughTheSerializer() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, OTP);

        assertFalse(otpServiceHasKey(), "A redeemed code must be gone");
    }

    @Test
    void shouldStillRejectAWrongOtp() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "999999"));

        assertTrue(otpServiceHasKey(), "A wrong guess must not consume the code");
    }

    @Test
    void shouldStillRejectAnExpiredOtp() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        clockOffsetSeconds.addAndGet(Duration.ofMinutes(16).toSeconds());

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, OTP));

        assertFalse(otpServiceHasKey());
    }

    @Test
    void shouldKeepTheFifteenMinuteTtl() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        long ttl = ttl(key());

        assertTrue(ttl > 895 && ttl <= 900, "Expected roughly 900 seconds but found " + ttl);
    }

    /**
     * The production failure, end to end.
     *
     * <p>An entry left behind by a process whose {@code OtpData} came from a
     * different classloader used to be unreadable: it deserialized into a
     * look-alike class with the same name that failed the identity check, and
     * the read threw instead of returning the pending code. Here the code must
     * simply still work.</p>
     */
    @Test
    void shouldVerifyAnOtpIssuedByADifferentClassloader() throws Exception {

        Instant now = Instant.now();
        String hash = new OtpHasher(properties).hash(OTP);

        String jsonFromElsewhere = mapper.writeValueAsString(ForeignOtpData.instance(
                hash,
                EMAIL,
                OtpType.EMAIL,
                now,
                now.plusSeconds(Duration.ofMinutes(15).toSeconds()),
                0));

        keyspace.put(key(), new Entry(jsonFromElsewhere, now.plusSeconds(900)));

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, OTP);

        assertFalse(otpServiceHasKey());
    }

    private boolean otpServiceHasKey() {
        return live(key()) != null;
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

    private boolean storeIfAbsent(String key, String value, Duration ttl) {

        if (live(key) != null) {
            return false;
        }

        keyspace.put(key, new Entry(value, ttl == null ? null : now().plus(ttl)));

        return true;
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
