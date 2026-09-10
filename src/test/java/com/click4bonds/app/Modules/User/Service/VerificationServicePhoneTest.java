package com.click4bonds.app.Modules.User.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * The phone half of the verification flow. SMS delivery does not exist yet, so
 * the code is issued and stored and nothing is sent — it stays a secret all the
 * same.
 */
class VerificationServicePhoneTest {

    private InMemoryRedisService redis;
    private VerificationTestSupport.CapturingEmailProvider provider;
    private VerificationTestSupport.FakeUserService userService;
    private VerificationTestSupport.FakeUserVerificationService verificationService;
    private VerificationService service;
    private User user;
    private UserVerification verification;

    @BeforeEach
    void setUp() {

        OtpProperties properties = VerificationTestSupport.otpProperties();

        redis = new InMemoryRedisService();
        provider = new VerificationTestSupport.CapturingEmailProvider();
        userService = new VerificationTestSupport.FakeUserService();
        verificationService = new VerificationTestSupport.FakeUserVerificationService();

        OtpService otpService = VerificationTestSupport.otpService(redis, properties);

        service = new VerificationService(
                otpService,
                VerificationTestSupport.emailService(provider),
                userService,
                verificationService,
                properties);

        user = VerificationTestSupport.user(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                null);

        userService.register(user);
        verification = verificationService.register(user);
    }

    // --- issuing a code -------------------------------------------------

    @Test
    void shouldGenerateAndStoreTheCodeWithoutSendingIt() {

        VerificationResponse response =
                service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertEquals(VerificationService.PHONE_OTP_GENERATED, response.message());
        assertTrue(redis.exists(VerificationTestSupport.SMS_KEY));

        // There is no SMS provider, and the phone flow must not reach for the
        // email one as a stand-in.
        assertTrue(provider.sent().isEmpty(),
                "No delivery channel exists yet, so nothing may be delivered");
    }

    @Test
    void shouldNotPutTheCodeInTheResponse() {

        VerificationResponse response =
                service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertFalse(response.message().contains(VerificationTestSupport.OTP));
        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY),
                "Issuing a phone code must not touch the email namespace");
    }

    @Test
    void shouldNotChangeAnyVerificationStatusWhenACodeIsIssued() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
        assertNull(user.getMobileNumber(), "A number is only bound once it is proven");
    }

    @Test
    void shouldAcceptANumberWrittenWithSeparators() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, "+91 98765-43210");

        assertTrue(redis.exists(VerificationTestSupport.SMS_KEY));
    }

    @Test
    void shouldRefuseANumberRegisteredToAnotherAccount() {

        userService.alreadyTakenBySomeoneElse(VerificationTestSupport.PHONE);

        assertThrows(ConflictException.class,
                () -> service.sendPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE));

        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));
    }

    @Test
    void shouldRefuseANumberThatIsNotTheOneOnTheAccount() {

        user.setMobileNumber("+919000000000");

        assertThrows(ForbiddenException.class,
                () -> service.sendPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE));

        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));
    }

    @Test
    void shouldRejectAMalformedNumber() {

        assertThrows(BadRequestException.class,
                () -> service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, "12345"));
    }

    @Test
    void shouldEnforceTheResendCooldownImposedByTheOtpModule() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertThrows(OtpResendCooldownException.class,
                () -> service.sendPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE));
    }

    @Test
    void shouldNotIssueAnotherCodeOnceThePhoneIsVerified() {

        user.setMobileNumber(VerificationTestSupport.PHONE);
        verification.setPhoneStatus(VerificationStatus.VERIFIED);

        VerificationResponse response =
                service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertEquals(VerificationService.PHONE_ALREADY_VERIFIED, response.message());
        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));
    }

    // --- redeeming a code -----------------------------------------------

    @Test
    void shouldBindTheNumberAndMarkThePhoneVerifiedOnTheCorrectCode() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        VerificationResponse response = service.verifyPhoneOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.PHONE,
                VerificationTestSupport.OTP);

        assertEquals(VerificationService.PHONE_VERIFIED, response.message());
        assertEquals(VerificationStatus.VERIFIED, verification.getPhoneStatus());
        assertEquals(VerificationTestSupport.PHONE, user.getMobileNumber(),
                "The proven number becomes the account's number");
    }

    @Test
    void shouldAdvanceOnboardingToThePanStep() {

        user.setOnboardingStep(OnboardingStep.PHONE_VERIFICATION);

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);
        service.verifyPhoneOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.PHONE,
                VerificationTestSupport.OTP);

        assertEquals(OnboardingStep.PAN_VERIFICATION, user.getOnboardingStep());
    }

    @Test
    void shouldLeaveTheAccountUntouchedOnAWrongCode() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertThrows(InvalidOtpException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        "999999"));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
        assertNull(user.getMobileNumber(), "A failed attempt must not bind the number");
    }

    @Test
    void shouldLeaveTheAccountUntouchedOnAnExpiredCode() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        redis.advance(Duration.ofMinutes(16));

        assertThrows(InvalidOtpException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
        assertNull(user.getMobileNumber());
    }

    @Test
    void shouldNotAcceptAConsumedCodeTwice() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);
        service.verifyPhoneOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.PHONE,
                VerificationTestSupport.OTP);

        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));

        assertThrows(InvalidOtpException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        VerificationTestSupport.OTP));
    }

    @Test
    void shouldStopAcceptingCodesOnceTheAttemptLimitIsReached() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(InvalidOtpException.class,
                    () -> service.verifyPhoneOtp(
                            VerificationTestSupport.CLERK_ID,
                            VerificationTestSupport.PHONE,
                            "999999"));
        }

        assertThrows(OtpMaxAttemptsExceededException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        "999999"));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));
    }

    @Test
    void shouldRefuseANumberRegisteredToAnotherAccountAtVerificationToo() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        userService.alreadyTakenBySomeoneElse(VerificationTestSupport.PHONE);

        assertThrows(ConflictException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
    }

    @Test
    void shouldRejectAVerificationWithNoCodeOutstanding() {

        assertThrows(InvalidOtpException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
        assertNull(user.getMobileNumber());
    }
}
