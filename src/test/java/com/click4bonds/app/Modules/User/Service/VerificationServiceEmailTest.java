//package com.click4bonds.app.Modules.User.Service;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.time.Duration;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
//import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
//import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
//import com.click4bonds.app.Modules.Email.Model.EmailRequest;
//import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
//import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
//import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
//import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
//import com.click4bonds.app.Modules.OTP.Model.OtpType;
//import com.click4bonds.app.Modules.OTP.Service.OtpKeyFactory;
//import com.click4bonds.app.Modules.OTP.Service.OtpService;
//import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
//import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
//import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
//import com.click4bonds.app.Modules.User.Model.User;
//import com.click4bonds.app.Modules.User.Model.UserVerification;
//
/// **
// * The email half of the verification flow: issuing, delivering and redeeming a
// * code, and what each outcome does — or refuses to do — to the account.
// */
//class VerificationServiceEmailTest {
//
//    private InMemoryRedisService redis;
//    private VerificationTestSupport.CapturingEmailProvider provider;
//    private VerificationTestSupport.FakeUserService userService;
//    private VerificationTestSupport.FakeUserVerificationService verificationService;
//    private VerificationService service;
//    private User user;
//    private UserVerification verification;
//
//    @BeforeEach
//    void setUp() {
//
//        OtpProperties properties = VerificationTestSupport.otpProperties();
//
//        redis = new InMemoryRedisService();
//        provider = new VerificationTestSupport.CapturingEmailProvider();
//        userService = new VerificationTestSupport.FakeUserService();
//        verificationService = new VerificationTestSupport.FakeUserVerificationService();
//
//        OtpService otpService = VerificationTestSupport.otpService(redis, properties);
//
//        service = new VerificationService(
//                otpService,
//                VerificationTestSupport.emailService(provider),
//                userService,
//                verificationService,
//                properties);
//
//        user = VerificationTestSupport.user(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                null);
//
//        userService.register(user);
//        verification = verificationService.register(user);
//    }
//
//    // --- issuing a code -------------------------------------------------
//
//    @Test
//    void shouldStoreTheCodeInRedisUnderTheEmailNamespace() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertTrue(redis.exists(VerificationTestSupport.EMAIL_KEY));
//        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY),
//                "Issuing an email code must not touch the SMS namespace");
//    }
//
//    @Test
//    void shouldDeliverTheCodeToTheUsersOwnAddress() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        EmailRequest sent = provider.lastSent();
//
//        assertEquals(VerificationTestSupport.EMAIL, sent.to());
//        assertTrue(sent.html().contains(VerificationTestSupport.OTP),
//                "The code must reach the message body");
//        assertEquals(1, provider.sent().size(), "Exactly one email must be sent");
//    }
//
//    @Test
//    void shouldStateTheConfiguredExpiryInTheEmail() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        int configured = VerificationTestSupport.otpProperties().getExpiryMinutes();
//
//        assertTrue(provider.lastSent().html().contains(configured + " minutes"),
//                "The email must quote the validity the code really has");
//    }
//
//    @Test
//    void shouldNotPutTheCodeInTheResponse() {
//
//        VerificationResponse response =
//                service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertEquals(VerificationService.EMAIL_OTP_SENT, response.message());
//        assertFalse(response.message().contains(VerificationTestSupport.OTP),
//                "The response must never carry the code");
//    }
//
//    @Test
//    void shouldNotChangeAnyVerificationStatusWhenACodeIsIssued() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus(),
//                "Issuing a code is not evidence of anything");
//        assertEquals(OnboardingStep.EMAIL_VERIFICATION, user.getOnboardingStep());
//    }
//
//    @Test
//    void shouldAcceptAnyCasingOfTheUsersOwnAddress() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, "User@Example.COM");
//
//        assertTrue(redis.exists(VerificationTestSupport.EMAIL_KEY));
//    }
//
//    @Test
//    void shouldRefuseAnAddressTheUserDoesNotOwn() {
//
//        assertThrows(ForbiddenException.class,
//                () -> service.sendEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        "someone.else@example.com"));
//
//        assertTrue(provider.sent().isEmpty(), "Nothing may be sent to a foreign address");
//        assertFalse(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, "someone.else@example.com")));
//    }
//
//    @Test
//    void shouldRejectAMalformedAddress() {
//
//        assertThrows(BadRequestException.class,
//                () -> service.sendEmailOtp(VerificationTestSupport.CLERK_ID, "not-an-email"));
//
//        assertTrue(provider.sent().isEmpty());
//    }
//
//    @Test
//    void shouldEnforceTheResendCooldownImposedByTheOtpModule() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertThrows(OtpResendCooldownException.class,
//                () -> service.sendEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.EMAIL));
//
//        assertEquals(1, provider.sent().size(), "A throttled request must not send anything");
//    }
//
//    @Test
//    void shouldLetTheCooldownElapse() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        redis.advance(Duration.ofSeconds(61));
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertEquals(2, provider.sent().size());
//    }
//
//    @Test
//    void shouldNotIssueAnotherCodeOnceTheEmailIsVerified() {
//
//        verification.setEmailStatus(VerificationStatus.VERIFIED);
//
//        VerificationResponse response =
//                service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertEquals(VerificationService.EMAIL_ALREADY_VERIFIED, response.message());
//        assertTrue(provider.sent().isEmpty(), "No further code may be issued");
//        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY));
//    }
//
//    // --- redeeming a code -----------------------------------------------
//
//    @Test
//    void shouldMarkTheEmailVerifiedOnTheCorrectCode() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        VerificationResponse response = service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        assertEquals(VerificationService.EMAIL_VERIFIED, response.message());
//        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus());
//    }
//
//    @Test
//    void shouldAdvanceOnboardingToThePhoneStep() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//        service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        assertEquals(OnboardingStep.PHONE_VERIFICATION, user.getOnboardingStep());
//    }
//
//    @Test
//    void shouldNotSkipOverAStepTheUserAlreadyPassed() {
//
//        user.setOnboardingStep(OnboardingStep.PAN_VERIFICATION);
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//        service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus());
//        assertEquals(OnboardingStep.PAN_VERIFICATION, user.getOnboardingStep(),
//                "Onboarding must never move backwards");
//    }
//
//    @Test
//    void shouldLeaveTheAccountUntouchedOnAWrongCode() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertThrows(InvalidOtpException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.EMAIL,
//                        "999999"));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
//        assertEquals(OnboardingStep.EMAIL_VERIFICATION, user.getOnboardingStep());
//    }
//
//    @Test
//    void shouldLeaveTheAccountUntouchedOnAnExpiredCode() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        redis.advance(Duration.ofMinutes(16));
//
//        assertThrows(InvalidOtpException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.EMAIL,
//                        VerificationTestSupport.OTP));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
//        assertEquals(OnboardingStep.EMAIL_VERIFICATION, user.getOnboardingStep());
//    }
//
//    @Test
//    void shouldNotAcceptAConsumedCodeTwice() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//        service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY),
//                "A redeemed code must be gone");
//
//        assertThrows(InvalidOtpException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.EMAIL,
//                        VerificationTestSupport.OTP));
//    }
//
//    @Test
//    void shouldStopAcceptingCodesOnceTheAttemptLimitIsReached() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        for (int attempt = 0; attempt < 4; attempt++) {
//            assertThrows(InvalidOtpException.class,
//                    () -> service.verifyEmailOtp(
//                            VerificationTestSupport.CLERK_ID,
//                            VerificationTestSupport.EMAIL,
//                            "999999"));
//        }
//
//        assertThrows(OtpMaxAttemptsExceededException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.EMAIL,
//                        "999999"));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
//        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY));
//    }
//
//    @Test
//    void shouldRefuseToRedeemAnAddressTheUserDoesNotOwn() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertThrows(ForbiddenException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        "someone.else@example.com",
//                        VerificationTestSupport.OTP));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
//    }
//
//    @Test
//    void shouldNotResetAVerifiedEmailWhenAnotherCodeIsRequested() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//        service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        redis.advance(Duration.ofSeconds(61));
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus(),
//                "Issuing a code must never undo a verification");
//    }
//}


package com.click4bonds.app.Modules.User.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.Email.Model.EmailRequest;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.OtpKeyFactory;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.Sms.service.SmsService;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * The email half of the verification flow: issuing, delivering and redeeming a
 * code, and what each outcome does — or refuses to do — to the account.
 */
class VerificationServiceEmailTest {

    private InMemoryRedisService redis;
    private VerificationTestSupport.CapturingEmailProvider provider;
    private VerificationTestSupport.FakeUserService userService;
    private VerificationTestSupport.FakeUserVerificationService verificationService;
    private SmsService smsService;
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
        smsService = mock(SmsService.class);

        OtpService otpService = VerificationTestSupport.otpService(redis, properties);

        // Argument order follows the field order in VerificationService.
        service = new VerificationService(
                otpService,
                VerificationTestSupport.emailService(provider),
                userService,
                verificationService,
                properties,
                smsService);

        user = VerificationTestSupport.user(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                null);

        userService.register(user);
        verification = verificationService.register(user);
    }

    // --- issuing a code -------------------------------------------------

    @Test
    void shouldStoreTheCodeInRedisUnderTheEmailNamespace() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertTrue(redis.exists(VerificationTestSupport.EMAIL_KEY));
        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY),
                "Issuing an email code must not touch the SMS namespace");
    }

    @Test
    void shouldDeliverTheCodeToTheUsersOwnAddress() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        EmailRequest sent = provider.lastSent();

        assertEquals(VerificationTestSupport.EMAIL, sent.to());
        assertTrue(sent.html().contains(VerificationTestSupport.OTP),
                "The code must reach the message body");
        assertEquals(1, provider.sent().size(), "Exactly one email must be sent");
    }

    @Test
    void shouldNotSendAnSmsWhenAnEmailCodeIsIssued() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        verifyNoInteractions(smsService);
    }

    @Test
    void shouldStateTheConfiguredExpiryInTheEmail() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        int configured = VerificationTestSupport.otpProperties().getExpiryMinutes();

        assertTrue(provider.lastSent().html().contains(configured + " minutes"),
                "The email must quote the validity the code really has");
    }

    @Test
    void shouldNotPutTheCodeInTheResponse() {

        VerificationResponse response =
                service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertEquals(VerificationService.EMAIL_OTP_SENT, response.message());
        assertFalse(response.message().contains(VerificationTestSupport.OTP),
                "The response must never carry the code");
    }

    @Test
    void shouldNotChangeAnyVerificationStatusWhenACodeIsIssued() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus(),
                "Issuing a code is not evidence of anything");
        assertEquals(OnboardingStep.EMAIL_VERIFICATION, user.getOnboardingStep());
    }

    @Test
    void shouldAcceptAnyCasingOfTheUsersOwnAddress() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, "User@Example.COM");

        assertTrue(redis.exists(VerificationTestSupport.EMAIL_KEY));
    }

    @Test
    void shouldRefuseAnAddressTheUserDoesNotOwn() {

        assertThrows(ForbiddenException.class,
                () -> service.sendEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        "someone.else@example.com"));

        assertTrue(provider.sent().isEmpty(), "Nothing may be sent to a foreign address");
        assertFalse(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, "someone.else@example.com")));
    }

    @Test
    void shouldRejectAMalformedAddress() {

        assertThrows(BadRequestException.class,
                () -> service.sendEmailOtp(VerificationTestSupport.CLERK_ID, "not-an-email"));

        assertTrue(provider.sent().isEmpty());
    }

    @Test
    void shouldEnforceTheResendCooldownImposedByTheOtpModule() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertThrows(OtpResendCooldownException.class,
                () -> service.sendEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.EMAIL));

        assertEquals(1, provider.sent().size(), "A throttled request must not send anything");
    }

    @Test
    void shouldLetTheCooldownElapse() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        redis.advance(Duration.ofSeconds(61));

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertEquals(2, provider.sent().size());
    }

    @Test
    void shouldNotIssueAnotherCodeOnceTheEmailIsVerified() {

        verification.setEmailStatus(VerificationStatus.VERIFIED);

        VerificationResponse response =
                service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertEquals(VerificationService.EMAIL_ALREADY_VERIFIED, response.message());
        assertTrue(provider.sent().isEmpty(), "No further code may be issued");
        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY));
    }

    // --- redeeming a code -----------------------------------------------

    @Test
    void shouldMarkTheEmailVerifiedOnTheCorrectCode() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        VerificationResponse response = service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        assertEquals(VerificationService.EMAIL_VERIFIED, response.message());
        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus());
    }

    @Test
    void shouldAdvanceOnboardingToThePhoneStep() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
        service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        assertEquals(OnboardingStep.PHONE_VERIFICATION, user.getOnboardingStep());
    }

    @Test
    void shouldNotSkipOverAStepTheUserAlreadyPassed() {

        user.setOnboardingStep(OnboardingStep.PAN_VERIFICATION);

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
        service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus());
        assertEquals(OnboardingStep.PAN_VERIFICATION, user.getOnboardingStep(),
                "Onboarding must never move backwards");
    }

    @Test
    void shouldLeaveTheAccountUntouchedOnAWrongCode() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertThrows(InvalidOtpException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.EMAIL,
                        "999999"));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
        assertEquals(OnboardingStep.EMAIL_VERIFICATION, user.getOnboardingStep());
    }

    @Test
    void shouldLeaveTheAccountUntouchedOnAnExpiredCode() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        redis.advance(Duration.ofMinutes(16));

        assertThrows(InvalidOtpException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.EMAIL,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
        assertEquals(OnboardingStep.EMAIL_VERIFICATION, user.getOnboardingStep());
    }

    @Test
    void shouldNotAcceptAConsumedCodeTwice() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
        service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY),
                "A redeemed code must be gone");

        assertThrows(InvalidOtpException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.EMAIL,
                        VerificationTestSupport.OTP));
    }

    @Test
    void shouldStopAcceptingCodesOnceTheAttemptLimitIsReached() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(InvalidOtpException.class,
                    () -> service.verifyEmailOtp(
                            VerificationTestSupport.CLERK_ID,
                            VerificationTestSupport.EMAIL,
                            "999999"));
        }

        assertThrows(OtpMaxAttemptsExceededException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.EMAIL,
                        "999999"));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
        assertFalse(redis.exists(VerificationTestSupport.EMAIL_KEY));
    }

    @Test
    void shouldRefuseToRedeemAnAddressTheUserDoesNotOwn() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertThrows(ForbiddenException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        "someone.else@example.com",
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
    }

    @Test
    void shouldNotResetAVerifiedEmailWhenAnotherCodeIsRequested() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
        service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        redis.advance(Duration.ofSeconds(61));
        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus(),
                "Issuing a code must never undo a verification");
    }
}