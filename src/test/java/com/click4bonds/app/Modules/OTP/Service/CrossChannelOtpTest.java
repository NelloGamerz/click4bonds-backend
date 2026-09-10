package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * Email and SMS OTPs must never be interchangeable, even when they are issued
 * for the same person at the same moment.
 */
class CrossChannelOtpTest {

    private static final String EMAIL = OtpTestSupport.EMAIL;
    private static final String PHONE = OtpTestSupport.PHONE;

    private OtpService otpService;

    @BeforeEach
    void setUp() {

        otpService = OtpTestSupport.service(
                new InMemoryRedisService(),
                OtpTestSupport.sequentialGenerator(),
                OtpTestSupport.defaultProperties());
    }

    @Test
    void shouldKeepEmailAndSmsOtpAliveAtTheSameTime() {

        String emailOtp = otpService.generateOtp(OtpType.EMAIL, EMAIL);
        String smsOtp = otpService.generateOtp(OtpType.SMS, PHONE);

        assertNotEquals(emailOtp, smsOtp);
    }

    @Test
    void shouldNotLetAnEmailOtpVerifyAnSmsRequest() {

        String emailOtp = otpService.generateOtp(OtpType.EMAIL, EMAIL);
        otpService.generateOtp(OtpType.SMS, PHONE);

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.SMS, PHONE, emailOtp));
    }

    @Test
    void shouldNotLetAnSmsOtpVerifyAnEmailRequest() {

        otpService.generateOtp(OtpType.EMAIL, EMAIL);
        String smsOtp = otpService.generateOtp(OtpType.SMS, PHONE);

        assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(OtpType.EMAIL, EMAIL, smsOtp));
    }

    @Test
    void shouldNotInvalidateTheSmsOtpWhenAnEmailOtpIsIssued() {

        String smsOtp = otpService.generateOtp(OtpType.SMS, PHONE);
        otpService.generateOtp(OtpType.EMAIL, EMAIL);

        otpService.verifyOtp(OtpType.SMS, PHONE, smsOtp);
    }

    @Test
    void shouldNotInvalidateTheEmailOtpWhenAnSmsOtpIsIssued() {

        String emailOtp = otpService.generateOtp(OtpType.EMAIL, EMAIL);
        otpService.generateOtp(OtpType.SMS, PHONE);

        otpService.verifyOtp(OtpType.EMAIL, EMAIL, emailOtp);
    }

    @Test
    void shouldLetBothChannelsVerifyTheirOwnOtpIndependently() {

        String emailOtp = otpService.generateOtp(OtpType.EMAIL, EMAIL);
        String smsOtp = otpService.generateOtp(OtpType.SMS, PHONE);

        // Consuming one must leave the other untouched.
        otpService.verifyOtp(OtpType.EMAIL, EMAIL, emailOtp);
        otpService.verifyOtp(OtpType.SMS, PHONE, smsOtp);
    }

    @Test
    void shouldReportTheChannelOfTheStoredOtp() {

        InMemoryRedisService redis = new InMemoryRedisService();
        OtpService service = OtpTestSupport.service(
                redis, OtpTestSupport.sequentialGenerator(), OtpTestSupport.defaultProperties());

        service.generateOtp(OtpType.EMAIL, EMAIL);
        service.generateOtp(OtpType.SMS, PHONE);

        assertTrue(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com")));
        assertTrue(redis.exists(OtpKeyFactory.otpKey(OtpType.SMS, "+919876543210")));
    }
}
