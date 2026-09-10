package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
import com.click4bonds.app.Modules.OTP.Model.OtpData;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * An OTP may be guessed at most {@code otp.max-attempts} times (5 by
 * default) before it is destroyed.
 */
class AttemptLimitTest {

    private static final String EMAIL = OtpTestSupport.EMAIL;
    private static final String KEY = OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com");
    private static final String CORRECT = "123456";
    private static final String WRONG = "999999";

    private InMemoryRedisService redis;
    private OtpService otpService;

    @BeforeEach
    void setUp() {

        redis = new InMemoryRedisService();
        otpService = OtpTestSupport.service(
                redis,
                OtpTestSupport.fixedGenerator(CORRECT),
                OtpTestSupport.defaultProperties());
    }

    @Test
    void shouldTolerateAttemptsBelowTheLimit() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(InvalidOtpException.class,
                    () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG));
        }

        OtpData stored = redis.get(KEY, OtpData.class).orElseThrow();

        assertEquals(4, stored.getAttemptCount());
        assertTrue(redis.exists(KEY), "Four bad guesses must not burn a five-attempt OTP");
    }

    @Test
    void shouldDestroyTheOtpOnTheFinalAllowedAttempt() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(InvalidOtpException.class,
                    () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG));
        }

        assertThrows(OtpMaxAttemptsExceededException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG));

        assertFalse(redis.exists(KEY), "The OTP must be gone once the limit is reached");
    }

    @Test
    void shouldBlockVerificationOnceTheLimitIsReached() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(OtpException.class,
                    () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG));
        }

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG),
                "Further guessing must fail outright");

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, CORRECT),
                "Even the original code is worthless once the OTP was destroyed");
    }

    @Test
    void shouldNotExtendTheOtpLifetimeOnAFailedAttempt() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(120));

        long ttlBefore = redis.getTtl(KEY);

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG));

        long ttlAfter = redis.getTtl(KEY);

        assertTrue(ttlAfter <= ttlBefore,
                "A wrong guess must not push the expiry out: " + ttlBefore + " -> " + ttlAfter);
    }

    @Test
    void shouldCountAttemptsPerChannelIndependently() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);
        otpService.generateOtp(OtpType.SMS, OtpTestSupport.PHONE);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(InvalidOtpException.class,
                    () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, WRONG));
        }

        OtpData sms = redis.get(OtpKeyFactory.otpKey(OtpType.SMS, "+919876543210"), OtpData.class).orElseThrow();

        assertEquals(0, sms.getAttemptCount(),
                "Burning down the email OTP must not charge the SMS OTP");
    }
}
