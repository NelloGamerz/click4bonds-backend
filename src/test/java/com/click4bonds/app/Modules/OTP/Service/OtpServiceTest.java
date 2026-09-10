package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Model.OtpData;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * Behaviour of {@link OtpService} against an in-memory Redis. No server, no
 * delivery provider, no authentication — just generation, storage and
 * verification.
 */
class OtpServiceTest {

    private static final String EMAIL = OtpTestSupport.EMAIL;
    private static final String PHONE = OtpTestSupport.PHONE;
    private static final String EMAIL_KEY = OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com");
    private static final String SMS_KEY = OtpKeyFactory.otpKey(OtpType.SMS, "+919876543210");

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
    void shouldGenerateEmailOtpUnderTheEmailKey() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertEquals(6, otp.length());
        assertTrue(redis.exists(EMAIL_KEY));
        assertFalse(redis.exists(SMS_KEY),
                "Generating an email OTP must not touch the SMS namespace");
    }

    @Test
    void shouldGenerateSmsOtpUnderTheSmsKey() {

        String otp = otpService.generateOtp(OtpType.SMS, PHONE);

        assertEquals(6, otp.length());
        assertTrue(redis.exists(SMS_KEY));
        assertFalse(redis.exists(EMAIL_KEY),
                "Generating an SMS OTP must not touch the email namespace");
    }

    @Test
    void shouldGiveTheOtpAFifteenMinuteTtl() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        long ttl = redis.getTtl(EMAIL_KEY);

        assertTrue(ttl > 895 && ttl <= 900,
                "Expected roughly 900 seconds of TTL but found " + ttl);
    }

    @Test
    void shouldNeverStoreThePlaintextOtp() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        OtpData stored = redis.get(EMAIL_KEY, OtpData.class).orElseThrow();

        assertNotEquals(otp, stored.getOtpHash(), "The code must not be stored as-is");
        assertFalse(stored.getOtpHash().contains(otp), "The hash must not embed the code");
        assertEquals(OtpType.EMAIL, stored.getOtpType());
        assertEquals(EMAIL, stored.getIdentifier());
        assertEquals(0, stored.getAttemptCount());
    }

    @Test
    void shouldVerifyACorrectOtp() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, otp);
    }

    @Test
    void shouldRejectAnIncorrectOtp() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "999999"));
    }

    @Test
    void shouldRejectVerificationWhenNoOtpWasIssued() {

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "123456"));
    }

    @Test
    void shouldRejectVerificationAfterExpiry() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofMinutes(16));

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, otp));
        assertFalse(redis.exists(EMAIL_KEY), "An expired OTP must be gone");
    }

    @Test
    void shouldConsumeTheOtpOnSuccessfulVerification() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, otp);

        assertFalse(redis.exists(EMAIL_KEY), "A verified OTP is single-use");
    }

    @Test
    void shouldRejectReusingAConsumedOtp() {

        String otp = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, otp);

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, otp));
    }

    @Test
    void shouldNormalizeTheEmailBeforeAddressingRedis() {

        String otp = otpService.generateOtp(OtpType.EMAIL, " User@Example.COM ");

        assertTrue(redis.exists(EMAIL_KEY));
        otpService.verifyOtp(OtpType.EMAIL, "user@EXAMPLE.com", otp);
    }

    @Test
    void shouldNormalizeThePhoneNumberBeforeAddressingRedis() {

        String otp = otpService.generateOtp(OtpType.SMS, "+91 98765-43210");

        assertTrue(redis.exists(SMS_KEY));
        otpService.verifyOtp(OtpType.SMS, "+919876543210", otp);
    }

    @Test
    void shouldReplaceThePendingOtpOnRegeneration() {

        String first = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(61));

        String second = otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertNotEquals(first, second, "A new OTP must be minted");

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, first),
                "The superseded OTP must stop working immediately");

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, second);
    }

    @Test
    void shouldGiveTheRegeneratedOtpAFullTtlAgain() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        redis.advance(Duration.ofSeconds(120));

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        long ttl = redis.getTtl(EMAIL_KEY);

        assertTrue(ttl > 895 && ttl <= 900,
                "Regeneration must restart the 15 minute window but found " + ttl);
    }

    @Test
    void shouldRejectMalformedOtpValues() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, null));
        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "   "));
        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "12345"));
        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "1234567"));
        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, "12a456"));

        assertTrue(redis.exists(EMAIL_KEY),
                "A malformed submission must not consume the pending OTP");
    }

    @Test
    void shouldRejectInvalidIdentifiers() {

        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.EMAIL, null));
        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.EMAIL, "not-an-email"));
        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.EMAIL, "user@example"));
        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.SMS, "12345"));
        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.SMS, "+91-ABCDE-43210"));
        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(null, EMAIL));
    }

    @Test
    void shouldRejectIdentifiersThatCouldForgeAKeySegment() {

        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.EMAIL, "user@example.com:otp:sms:x"));
        assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(OtpType.SMS, "+919876543210:x"));
    }
}
