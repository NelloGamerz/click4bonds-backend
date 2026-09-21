//package com.click4bonds.app.Modules.User.Service;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.slf4j.LoggerFactory;
//
//import ch.qos.logback.classic.Logger;
//import ch.qos.logback.classic.spi.ILoggingEvent;
//import ch.qos.logback.core.read.ListAppender;
//
//import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
//import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
//import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
//import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
//import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
//import com.click4bonds.app.Modules.OTP.Model.OtpType;
//import com.click4bonds.app.Modules.OTP.Service.OtpKeyFactory;
//import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
//import com.click4bonds.app.Modules.User.Model.User;
//import com.click4bonds.app.Modules.User.Model.UserVerification;
//
/// **
// * What the flow refuses to do: act for somebody else, let one channel's code
// * stand in for the other's, or let a code end up in a log line.
// */
//class VerificationServiceSecurityTest {
//
//    private static final String OTHER_CLERK_ID = "user_clerk_2";
//    private static final String OTHER_EMAIL = "other@example.com";
//
//    private InMemoryRedisService redis;
//    private VerificationTestSupport.FakeUserService userService;
//    private VerificationTestSupport.FakeUserVerificationService verificationService;
//    private VerificationService service;
//
//    private User user;
//    private User other;
//    private UserVerification verification;
//    private UserVerification otherVerification;
//
//    private Logger rootLogger;
//    private ListAppender<ILoggingEvent> appender;
//
//    @BeforeEach
//    void setUp() {
//
//        OtpProperties properties = VerificationTestSupport.otpProperties();
//
//        redis = new InMemoryRedisService();
//        userService = new VerificationTestSupport.FakeUserService();
//        verificationService = new VerificationTestSupport.FakeUserVerificationService();
//
//        service = new VerificationService(
//                VerificationTestSupport.otpService(redis, properties),
//                VerificationTestSupport.emailService(
//                        new VerificationTestSupport.CapturingEmailProvider()),
//                userService,
//                verificationService,
//                properties);
//
//        user = VerificationTestSupport.user(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                null);
//
//        other = VerificationTestSupport.user(OTHER_CLERK_ID, OTHER_EMAIL, null);
//
//        userService.register(user).register(other);
//        verification = verificationService.register(user);
//        otherVerification = verificationService.register(other);
//
//        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
//        appender = new ListAppender<>();
//        appender.start();
//        rootLogger.addAppender(appender);
//    }
//
//    @AfterEach
//    void tearDown() {
//        rootLogger.detachAppender(appender);
//    }
//
//    @Test
//    void shouldNotLetAUserRequestACodeForSomebodyElsesAddress() {
//
//        assertThrows(ForbiddenException.class,
//                () -> service.sendEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        OTHER_EMAIL));
//
//        assertFalse(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, OTHER_EMAIL)));
//    }
//
//    @Test
//    void shouldNotLetAUserRedeemACodeForSomebodyElsesAddress() {
//
//        // A code that really is outstanding, for the other account.
//        service.sendEmailOtp(OTHER_CLERK_ID, OTHER_EMAIL);
//
//        assertThrows(ForbiddenException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        OTHER_EMAIL,
//                        VerificationTestSupport.OTP));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
//        assertEquals(VerificationStatus.NOT_STARTED, otherVerification.getEmailStatus(),
//                "The owner's own code must survive the attempt untouched");
//    }
//
//    @Test
//    void shouldNotLetAUserBindANumberAnotherAccountAlreadyHolds() {
//
//        userService.alreadyTakenBySomeoneElse(VerificationTestSupport.PHONE);
//
//        assertThrows(ConflictException.class,
//                () -> service.sendPhoneOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.PHONE));
//
//        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));
//        assertNull(user.getMobileNumber());
//    }
//
//    @Test
//    void shouldKeepEachUsersCodesUnderTheirOwnIdentifier() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//        service.sendEmailOtp(OTHER_CLERK_ID, OTHER_EMAIL);
//
//        assertTrue(redis.exists(VerificationTestSupport.EMAIL_KEY));
//        assertTrue(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, OTHER_EMAIL)));
//
//        service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus());
//        assertEquals(VerificationStatus.NOT_STARTED, otherVerification.getEmailStatus(),
//                "Redeeming one account's code must not touch the other's");
//    }
//
//    @Test
//    void shouldNotLetAnEmailCodeVerifyThePhoneFlow() {
//
//        user.setMobileNumber(VerificationTestSupport.PHONE);
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//
//        assertThrows(InvalidOtpException.class,
//                () -> service.verifyPhoneOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.PHONE,
//                        VerificationTestSupport.OTP));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());
//    }
//
//    @Test
//    void shouldNotLetAPhoneCodeVerifyTheEmailFlow() {
//
//        user.setMobileNumber(VerificationTestSupport.PHONE);
//
//        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);
//
//        assertThrows(InvalidOtpException.class,
//                () -> service.verifyEmailOtp(
//                        VerificationTestSupport.CLERK_ID,
//                        VerificationTestSupport.EMAIL,
//                        VerificationTestSupport.OTP));
//
//        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
//    }
//
//    @Test
//    void shouldNeverWriteTheCodeToTheLog() {
//
//        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
//        service.verifyEmailOtp(
//                VerificationTestSupport.CLERK_ID,
//                VerificationTestSupport.EMAIL,
//                VerificationTestSupport.OTP);
//
//        assertFalse(appender.list.isEmpty(), "The flow must actually log something");
//
//        for (ILoggingEvent event : appender.list) {
//
//            assertFalse(event.getFormattedMessage().contains(VerificationTestSupport.OTP),
//                    "A log line carried the code: " + event.getFormattedMessage());
//
//            if (event.getThrowableProxy() != null) {
//                assertFalse(event.getThrowableProxy().getMessage().contains(VerificationTestSupport.OTP),
//                        "A stack trace carried the code");
//            }
//        }
//    }
//}


package com.click4bonds.app.Modules.User.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.OtpKeyFactory;
import com.click4bonds.app.Modules.Sms.service.SmsService;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * What the flow refuses to do: act for somebody else, let one channel's code
 * stand in for the other's, or let a code end up in a log line.
 */
class VerificationServiceSecurityTest {

    private static final String OTHER_CLERK_ID = "user_clerk_2";
    private static final String OTHER_EMAIL = "other@example.com";

    private InMemoryRedisService redis;
    private VerificationTestSupport.FakeUserService userService;
    private VerificationTestSupport.FakeUserVerificationService verificationService;
    private SmsService smsService;
    private VerificationService service;

    private User user;
    private User other;
    private UserVerification verification;
    private UserVerification otherVerification;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {

        OtpProperties properties = VerificationTestSupport.otpProperties();

        redis = new InMemoryRedisService();
        userService = new VerificationTestSupport.FakeUserService();
        verificationService = new VerificationTestSupport.FakeUserVerificationService();
        smsService = mock(SmsService.class);

        service = new VerificationService(
                VerificationTestSupport.otpService(redis, properties),
                VerificationTestSupport.emailService(
                        new VerificationTestSupport.CapturingEmailProvider()),
                userService,
                verificationService,
                properties,
                smsService);

        user = VerificationTestSupport.user(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                null);

        other = VerificationTestSupport.user(OTHER_CLERK_ID, OTHER_EMAIL, null);

        userService.register(user).register(other);
        verification = verificationService.register(user);
        otherVerification = verificationService.register(other);

        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void shouldNotLetAUserRequestACodeForSomebodyElsesAddress() {

        assertThrows(ForbiddenException.class,
                () -> service.sendEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        OTHER_EMAIL));

        assertFalse(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, OTHER_EMAIL)));
    }

    @Test
    void shouldNotLetAUserRedeemACodeForSomebodyElsesAddress() {

        // A code that really is outstanding, for the other account.
        service.sendEmailOtp(OTHER_CLERK_ID, OTHER_EMAIL);

        assertThrows(ForbiddenException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        OTHER_EMAIL,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
        assertEquals(VerificationStatus.NOT_STARTED, otherVerification.getEmailStatus(),
                "The owner's own code must survive the attempt untouched");
    }

    @Test
    void shouldNotLetAUserBindANumberAnotherAccountAlreadyHolds() {

        userService.alreadyTakenBySomeoneElse(VerificationTestSupport.PHONE);

        assertThrows(ConflictException.class,
                () -> service.sendPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE));

        assertFalse(redis.exists(VerificationTestSupport.SMS_KEY));
        assertNull(user.getMobileNumber());

        // Nobody may be texted a code for a number they cannot claim.
        verifyNoInteractions(smsService);
    }

    @Test
    void shouldNotTextANumberThatIsNotTheOneOnTheAccount() {

        user.setMobileNumber("+919000000000");

        assertThrows(ForbiddenException.class,
                () -> service.sendPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE));

        verifyNoInteractions(smsService);
    }

    @Test
    void shouldKeepEachUsersCodesUnderTheirOwnIdentifier() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
        service.sendEmailOtp(OTHER_CLERK_ID, OTHER_EMAIL);

        assertTrue(redis.exists(VerificationTestSupport.EMAIL_KEY));
        assertTrue(redis.exists(OtpKeyFactory.otpKey(OtpType.EMAIL, OTHER_EMAIL)));

        service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        assertEquals(VerificationStatus.VERIFIED, verification.getEmailStatus());
        assertEquals(VerificationStatus.NOT_STARTED, otherVerification.getEmailStatus(),
                "Redeeming one account's code must not touch the other's");
    }

    @Test
    void shouldNotLetAnEmailCodeVerifyThePhoneFlow() {

        user.setMobileNumber(VerificationTestSupport.PHONE);

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);

        assertThrows(InvalidOtpException.class,
                () -> service.verifyPhoneOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.PHONE,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getPhoneStatus());

        // Requesting an email code must never send an SMS.
        verifyNoInteractions(smsService);
    }

    @Test
    void shouldNotLetAPhoneCodeVerifyTheEmailFlow() {

        user.setMobileNumber(VerificationTestSupport.PHONE);

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);

        assertThrows(InvalidOtpException.class,
                () -> service.verifyEmailOtp(
                        VerificationTestSupport.CLERK_ID,
                        VerificationTestSupport.EMAIL,
                        VerificationTestSupport.OTP));

        assertEquals(VerificationStatus.NOT_STARTED, verification.getEmailStatus());
    }

    @Test
    void shouldNeverWriteTheEmailCodeToTheLog() {

        service.sendEmailOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.EMAIL);
        service.verifyEmailOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.EMAIL,
                VerificationTestSupport.OTP);

        assertNoLogLineCarriesTheCode();
    }

    @Test
    void shouldNeverWriteThePhoneCodeToTheLog() {

        service.sendPhoneOtp(VerificationTestSupport.CLERK_ID, VerificationTestSupport.PHONE);
        service.verifyPhoneOtp(
                VerificationTestSupport.CLERK_ID,
                VerificationTestSupport.PHONE,
                VerificationTestSupport.OTP);

        assertNoLogLineCarriesTheCode();
    }

    private void assertNoLogLineCarriesTheCode() {

        assertFalse(appender.list.isEmpty(), "The flow must actually log something");

        for (ILoggingEvent event : appender.list) {

            assertFalse(event.getFormattedMessage().contains(VerificationTestSupport.OTP),
                    "A log line carried the code: " + event.getFormattedMessage());

            if (event.getThrowableProxy() != null) {
                assertFalse(event.getThrowableProxy().getMessage().contains(VerificationTestSupport.OTP),
                        "A stack trace carried the code");
            }
        }
    }
}