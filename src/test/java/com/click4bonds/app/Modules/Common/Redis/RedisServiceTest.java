package com.click4bonds.app.Modules.Common.Redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.click4bonds.app.Config.RedisConfig;
import com.click4bonds.app.Modules.Common.Exceptions.RedisOperationException;
import com.click4bonds.app.Modules.OTP.Model.OtpData;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.OtpKeyFactory;
import com.click4bonds.testing.ForeignOtpData;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link RedisServiceImpl}. The template is mocked, so these
 * run without a Redis server.
 */
@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    private static final String KEY = OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com");
    private static final Instant CREATED = Instant.parse("2026-09-10T10:00:00Z");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper mapper;
    private RedisService redisService;

    @BeforeEach
    void setUp() {

        // Only the value-based operations reach opsForValue(); delete, exists
        // and getTtl do not, so the stub is lenient on purpose.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        mapper = new RedisConfig().redisObjectMapper();
        redisService = new RedisServiceImpl(redisTemplate, mapper);
    }

    private static OtpData otpData(String hash) {
        return new OtpData(hash, "user@example.com", OtpType.EMAIL, CREATED, CREATED.plusSeconds(900), 0);
    }

    @Test
    void shouldStoreValueAsJsonWithTtl() throws Exception {

        Duration ttl = Duration.ofMinutes(15);
        OtpData value = otpData("hash");

        redisService.set(KEY, value, ttl);

        verify(valueOperations).set(KEY, mapper.writeValueAsString(value), ttl);
    }

    @Test
    void shouldStoreValueWithoutTtlWhenTtlIsNull() throws Exception {

        OtpData value = otpData("hash");

        redisService.set(KEY, value, null);

        verify(valueOperations).set(KEY, mapper.writeValueAsString(value));
    }

    @Test
    void shouldReturnStoredValueAsTheRequestedType() {

        when(valueOperations.get(KEY)).thenReturn("{\"otpHash\":\"hash\",\"identifier\":\"user@example.com\","
                + "\"otpType\":\"EMAIL\",\"createdAt\":\"" + CREATED + "\",\"expiresAt\":\""
                + CREATED.plusSeconds(900) + "\",\"attemptCount\":2}");

        Optional<OtpData> value = redisService.get(KEY, OtpData.class);

        assertTrue(value.isPresent());
        assertSame(OtpData.class, value.get().getClass(),
                "The reader's type must win: the value is bound to it, never matched against it");
        assertEquals("hash", value.get().getOtpHash());
        assertEquals(2, value.get().getAttemptCount());
    }

    @Test
    void shouldReturnEmptyWhenKeyIsMissing() {

        when(valueOperations.get(KEY)).thenReturn(null);

        assertTrue(redisService.get(KEY, OtpData.class).isEmpty());
    }

    @Test
    void shouldRejectAValueThatCannotBeReadAsTheRequestedType() {

        when(valueOperations.get(KEY)).thenReturn("not-json");

        assertThrows(RedisOperationException.class,
                () -> redisService.get(KEY, OtpData.class));
    }

    @Test
    void shouldRejectANullValue() {

        assertThrows(RedisOperationException.class,
                () -> redisService.set(KEY, null, Duration.ofMinutes(1)));
    }

    /**
     * The regression this whole change exists for.
     *
     * <p>An entry written by a process whose {@code OtpData} came from a
     * different classloader — exactly what Spring Boot DevTools' restart
     * classloader produces — used to deserialize into a look-alike class. It
     * carried the same name, so the old identity check reported
     * "expected ...OtpData but found ...OtpData" and the read failed outright.
     * JSON carries no class identity, so the payload cannot cause that any
     * more.</p>
     */
    @Test
    void shouldReadAnEntryWrittenByADifferentClassloader() throws Exception {

        String json = jsonWrittenByForeignClassloader();

        when(valueOperations.get(KEY)).thenReturn(json);

        OtpData read = redisService.get(KEY, OtpData.class).orElseThrow();

        assertSame(OtpData.class, read.getClass());
        assertEquals("hash-from-another-classloader", read.getOtpHash());
        assertEquals(3, read.getAttemptCount());
        assertEquals(CREATED, read.getCreatedAt());
    }

    @Test
    void shouldDeleteKey() {

        redisService.delete(KEY);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void shouldReportExistence() {

        when(redisTemplate.hasKey(KEY)).thenReturn(true);
        assertTrue(redisService.exists(KEY));

        when(redisTemplate.hasKey(KEY)).thenReturn(false);
        assertFalse(redisService.exists(KEY));

        when(redisTemplate.hasKey(KEY)).thenReturn(null);
        assertFalse(redisService.exists(KEY));
    }

    @Test
    void shouldReturnRemainingTtlInSeconds() {

        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(900L);

        assertEquals(900L, redisService.getTtl(KEY));
    }

    @Test
    void shouldReportRedisTtlSentinels() {

        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(-1L);
        assertEquals(-1L, redisService.getTtl(KEY));

        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(-2L);
        assertEquals(-2L, redisService.getTtl(KEY));

        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(null);
        assertEquals(-2L, redisService.getTtl(KEY));
    }

    @Test
    void shouldOnlyCreateKeyWhenAbsent() throws Exception {

        String json = mapper.writeValueAsString(Boolean.TRUE);
        Duration ttl = Duration.ofSeconds(60);

        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(true);

        assertTrue(redisService.setIfAbsent(KEY, Boolean.TRUE, ttl));
        verify(valueOperations).setIfAbsent(KEY, json, ttl);

        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(false);

        assertFalse(redisService.setIfAbsent(KEY, Boolean.TRUE, ttl));
    }

    @Test
    void shouldWrapInfrastructureFailuresWithoutLeakingTheValue() {

        QueryTimeoutException failure = new QueryTimeoutException("timed out");
        when(valueOperations.get(KEY)).thenThrow(failure);

        RedisOperationException thrown = assertThrows(RedisOperationException.class,
                () -> redisService.get(KEY, OtpData.class));

        assertEquals(failure, thrown.getCause());
        assertFalse(thrown.getMessage().contains(KEY),
                "The key can carry PII and must not appear in the message");
    }

    /**
     * @return the JSON a process would have stored if its {@code OtpData} came
     *         from a different classloader than this one
     */
    private String jsonWrittenByForeignClassloader() throws Exception {

        return mapper.writeValueAsString(ForeignOtpData.instance(
                "hash-from-another-classloader",
                "user@example.com",
                OtpType.EMAIL,
                CREATED,
                CREATED.plusSeconds(900),
                3));
    }
}
