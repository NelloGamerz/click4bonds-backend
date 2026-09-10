package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * A resend is refused for {@code otp.resend-cooldown-seconds} (60 by default)
 * after the previous request — independently per channel.
 */
class CooldownTest {

    private static final String EMAIL = OtpTestSupport.EMAIL;
    private static final String PHONE = OtpTestSupport.PHONE;
    private static final String EMAIL_KEY = OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com");
    private static final String SMS_KEY = OtpKeyFactory.otpKey(OtpType.SMS, "+919876543210");
    private static final String EMAIL_COOLDOWN_KEY = OtpKeyFactory.cooldownKey(OtpType.EMAIL, "user@example.com");

    private InMemoryRedisService redis;
    private OtpService otpService;

    @BeforeEach
    void setUp() {

        redis = new InMemoryRedisService();
        otpService = OtpTestSupport.service(
                redis,
                OtpTestSupport.sequentialGenerator(),
                OtpTestSupport.defaultProperties());
    }

    @Test
    void shouldSetACooldownOfSixtySeconds() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        long ttl = redis.getTtl(EMAIL_COOLDOWN_KEY);

        assertTrue(ttl > 55 && ttl <= 60,
                "Expected roughly 60 seconds of cooldown but found " + ttl);
    }

    @Test
    void shouldRejectAnImmediateResend() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertThrows(OtpResendCooldownException.class,
                () -> otpService.generateOtp(OtpType.EMAIL, EMAIL));
    }

    @Test
    void shouldLeaveThePendingOtpIntactWhenAResendIsRefused() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertThrows(OtpResendCooldownException.class,
                () -> otpService.generateOtp(OtpType.EMAIL, EMAIL));

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, otp);
    }

    @Test
    void shouldAllowAResendOnceTheCooldownElapsed() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(61));

        assertDoesNotThrow(() -> otpService.generateOtp(OtpType.EMAIL, EMAIL));
    }

    @Test
    void shouldReplaceTheOldOtpOnResend() {

        String first = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(61));

        String second = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertNotEquals(first, second);
        assertTrue(redis.exists(EMAIL_KEY));
        otpService.verifyOtp(OtpType.EMAIL, EMAIL, second);
    }

    @Test
    void shouldRestartTheFullTtlOnResend() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(120));

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        long ttl = redis.getTtl(EMAIL_KEY);

        assertTrue(ttl > 895 && ttl <= 900,
                "A resend must grant a fresh 15 minutes but found " + ttl);
    }

    @Test
    void shouldNotLetAnEmailCooldownBlockSms() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertDoesNotThrow(() -> otpService.generateOtp(OtpType.SMS, PHONE));
        assertTrue(redis.exists(SMS_KEY));
    }

    @Test
    void shouldNotLetAnSmsCooldownBlockEmail() {

        otpService.generateOtp(OtpType.SMS, PHONE);

        assertDoesNotThrow(() -> otpService.generateOtp(OtpType.EMAIL, EMAIL));
        assertTrue(redis.exists(EMAIL_KEY));
    }

    @Test
    void shouldExpireTheCooldownBeforeTheOtp() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(61));

        assertEquals(-2L, redis.getTtl(EMAIL_COOLDOWN_KEY), "The cooldown key must be gone");
        assertTrue(redis.getTtl(EMAIL_KEY) > 0, "The OTP itself is still within its 15 minutes");
    }
}
