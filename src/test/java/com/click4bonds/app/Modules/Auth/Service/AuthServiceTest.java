package com.click4bonds.app.Modules.Auth.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.click4bonds.app.Modules.Auth.AuthTestSupport;
import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Auth.Dto.AuthResponse;
import com.click4bonds.app.Modules.Auth.Dto.SignupRequest;
import com.click4bonds.app.Modules.Auth.Exception.InvalidSessionException;
import com.click4bonds.app.Modules.Auth.Exception.OtpRateLimitedException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.OtpHasher;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Enums.AgeRange;
import com.click4bonds.app.Modules.User.Enums.CommunicationLanguage;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Enums.UserType;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * Signing in with a phone number, end to end through the real OTP module, real
 * JWT service and real session service, over an in-memory Redis.
 */
class AuthServiceTest {

    private static final String PHONE = AuthTestSupport.PHONE;
    private static final String OTP = "483920";
    private static final String CLIENT = "203.0.113.7";

    private InMemoryRedisService redis;
    private AuthProperties properties;
    private OtpProperties otpProperties;
    private AuthTestSupport.RecordingSmsService sms;
    private AuthTestSupport.FakeUserService users;
    private AuthTestSupport.FakeVerificationService verifications;
    private OtpService otpService;
    private AuthJwtService jwts;
    private AuthSessionService sessions;
    private AuthService auth;

    @BeforeEach
    void setUp() {

        redis = new InMemoryRedisService();

        properties = AuthTestSupport.properties();
        otpProperties = otpProperties();

        sms = new AuthTestSupport.RecordingSmsService();
        users = new AuthTestSupport.FakeUserService();
        verifications = new AuthTestSupport.FakeVerificationService();

        otpService = new OtpService(
                redis,
                () -> OTP,
                new OtpHasher(otpProperties),
                otpProperties);

        jwts = new AuthJwtService(properties);
        sessions = new AuthSessionService(redis, properties);

        auth = new AuthService(
                otpService,
                sms,
                users,
                verifications,
                jwts,
                sessions,
                properties,
                redis);
    }

    private static OtpProperties otpProperties() {

        OtpProperties properties = new OtpProperties();
        properties.setLength(6);
        properties.setExpiryMinutes(5);
        properties.setMaxAttempts(5);
        properties.setResendCooldownSeconds(60);
        properties.setHashSecret("test-only-otp-hash-secret");

        return properties;
    }

    private void signIn(String phone) {

        auth.sendPhoneOtp(phone, CLIENT);
        auth.verifyPhoneOtp(phone, OTP, "test-agent");
    }

    /**
     * Puts an account behind a number, which is now the precondition for every
     * code request: signing in looks an account up rather than creating one.
     */
    private User registered(String phone) {

        User user = AuthTestSupport.activeCustomer(UUID.randomUUID().toString(), phone);

        users.register(user);

        return user;
    }

    // ------------------------------------------------------------------
    // Signing up
    // ------------------------------------------------------------------

    private static SignupRequest signupRequest(String mobileNumber) {

        return new SignupRequest(
                "Karan",
                "Pareek",
                mobileNumber,
                AgeRange.AGE_31_40,
                UserType.INDIVIDUAL_RESIDENT,
                CommunicationLanguage.ENGLISH,
                true,
                true);
    }

    @Test
    void signupOpensAnAccountAndSendsTheFirstCode() {

        VerificationResponse response = auth.signup(signupRequest(PHONE), CLIENT);

        User created = users.getUserByMobileNumber(PHONE);

        // Everything the form asked for is on the account.
        assertEquals("Karan", created.getFirstName());
        assertEquals("Pareek", created.getLastName());
        assertEquals(AgeRange.AGE_31_40, created.getAgeRange());
        assertEquals(UserType.INDIVIDUAL_RESIDENT, created.getUserType());
        assertEquals(
                CommunicationLanguage.ENGLISH,
                created.getPreferredCommunicationLanguage());
        assertEquals(Boolean.TRUE, created.getWhatsappCommunicationConsent());
        assertEquals(Boolean.TRUE, created.getTermsAccepted());

        assertEquals(UserRole.CUSTOMER, created.getRole());
        assertEquals(UserStatus.ACTIVE, created.getStatus());

        // Signing up establishes the number, and proving it is the next thing
        // the account is asked to do.
        assertEquals(OnboardingStep.PHONE_VERIFICATION, created.getOnboardingStep());

        assertEquals(OTP, sms.lastOtpFor(PHONE));

        // The message says a code was sent, and nothing about the code.
        assertNotNull(response.message());
        assertFalse(response.message().contains(OTP));
    }

    @Test
    void signupStoresTheNumberInItsCanonicalForm() {

        auth.signup(signupRequest("+91 98765-43210"), CLIENT);

        // The same number typed with separators is the same number, and the
        // account is found by the form the OTP module normalises to.
        assertEquals(PHONE, users.getUserByMobileNumber(PHONE).getMobileNumber());
    }

    @Test
    void signingUpTwiceWithTheSameNumberIsRefused() {

        auth.signup(signupRequest(PHONE), CLIENT);

        assertThrows(ConflictException.class, () -> auth.signup(signupRequest(PHONE), CLIENT));

        // The refusal did not send a second code, nor disturb the account.
        assertEquals(OTP, sms.lastOtpFor(PHONE));
    }

    @Test
    void aSignedUpAccountSignsInWithTheCodeSignupSent() {

        auth.signup(signupRequest(PHONE), CLIENT);

        AuthService.IssuedSession issued = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        User created = users.getUserByMobileNumber(PHONE);

        assertEquals(created.getId(), issued.response().userId());
        assertTrue(verifications.wasPhoneMarkedVerified(created));

        // Onboarding began at the phone step, so proving the number moves the
        // account on rather than leaving it where it was.
        assertEquals(OnboardingStep.PAN_VERIFICATION, created.getOnboardingStep());
    }

    // ------------------------------------------------------------------
    // Sending a code
    // ------------------------------------------------------------------

    @Test
    void sendingACodeDeliversItBySms() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertEquals(OTP, sms.lastOtpFor(PHONE));
    }

    @Test
    void aNumberWithNoAccountIsToldToSignUp() {

        String unknown = "+919000000002";

        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> auth.sendPhoneOtp(unknown, CLIENT));

        assertEquals("User does not exist, please sign up", thrown.getMessage());

        // Nothing was sent, and nothing was created to send to.
        assertNull(sms.lastOtpFor(unknown));
        assertFalse(users.isMobileNumberClaimed(unknown));
    }

    @Test
    void issuingACodeCreatesNoAccount() {

        User existing = registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        // A code proves nothing, so the only account in existence is the one
        // that was signed up — no second one was conjured by asking.
        assertEquals(existing.getId(), users.getUserByMobileNumber(PHONE).getId());
    }

    @Test
    void aSecondCodeForTheSameNumberIsRefusedUntilTheCooldownElapses() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertThrows(OtpResendCooldownException.class, () -> auth.sendPhoneOtp(PHONE, CLIENT));
    }

    @Test
    void requestsFromOneAddressAreRateLimited() {

        properties.getOtp().setMaxRequestsPerWindow(2);

        registered("+919000000010");
        registered("+919000000011");
        registered("+919000000012");

        auth.sendPhoneOtp("+919000000010", CLIENT);
        auth.sendPhoneOtp("+919000000011", CLIENT);

        // The per-number cooldown does not stop a sweep across many numbers;
        // this is the limit that does.
        assertThrows(OtpRateLimitedException.class, () -> auth.sendPhoneOtp("+919000000012", CLIENT));
    }

    @Test
    void theRateLimitIsSpentBeforeANumberIsLookedUp() {

        properties.getOtp().setMaxRequestsPerWindow(1);

        // A number that has no account still costs the caller its allowance.
        // Were the lookup done first, an unknown number would be the cheapest
        // way to keep asking, and the answer would be free.
        assertThrows(
                ResourceNotFoundException.class,
                () -> auth.sendPhoneOtp("+919000000030", CLIENT));

        registered("+919000000031");

        assertThrows(
                OtpRateLimitedException.class,
                () -> auth.sendPhoneOtp("+919000000031", CLIENT));
    }

    @Test
    void anotherAddressHasItsOwnAllowance() {

        properties.getOtp().setMaxRequestsPerWindow(1);

        registered("+919000000020");
        registered("+919000000021");
        registered("+919000000022");

        auth.sendPhoneOtp("+919000000020", CLIENT);

        assertThrows(OtpRateLimitedException.class, () -> auth.sendPhoneOtp("+919000000021", CLIENT));

        // One noisy client must not lock everybody else out.
        auth.sendPhoneOtp("+919000000022", "198.51.100.9");
    }

    @Test
    void malformedNumbersDoNotSpendTheAllowance() {

        properties.getOtp().setMaxRequestsPerWindow(1);

        registered(PHONE);

        assertThrows(
                com.click4bonds.app.Modules.Common.Exceptions.BadRequestException.class,
                () -> auth.sendPhoneOtp("not-a-number", CLIENT));

        auth.sendPhoneOtp(PHONE, CLIENT);
    }

    // ------------------------------------------------------------------
    // Redeeming a code
    // ------------------------------------------------------------------

    @Test
    void aValidCodeOpensASessionForTheAccountBehindTheNumber() {

        User existing = registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        AuthService.IssuedSession issued = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        User signedIn = users.getUserById(issued.response().userId().toString());

        assertEquals(existing.getId(), signedIn.getId());
        assertEquals(PHONE, signedIn.getMobileNumber());
        assertEquals(UserRole.CUSTOMER, signedIn.getRole());
        assertEquals(UserStatus.ACTIVE, signedIn.getStatus());

        // The response describes that same account.
        assertEquals(existing.getId(), issued.response().userId());

        // The number was proven by signing in, so the record says so.
        assertTrue(verifications.wasPhoneMarkedVerified(signedIn));

        // A session exists under the identifier handed back for the cookie.
        assertTrue(sessions.resolve(issued.sessionId()).isPresent());
    }

    @Test
    void redeemingACodeCannotCreateAnAccount() {

        // A code exists for this number — it could have been issued before the
        // account was removed — but no account does. A code is not a licence to
        // create one, so the redemption is refused rather than answered with a
        // new account.
        otpService.generateOtp(OtpType.SMS, PHONE);

        assertThrows(
                ResourceNotFoundException.class,
                () -> auth.verifyPhoneOtp(PHONE, OTP, "test-agent"));

        assertFalse(users.isMobileNumberClaimed(PHONE));
    }

    @Test
    void theAccessTokenNamesTheUserByIdentifier() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        AuthService.IssuedSession issued = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        Jwt decoded = jwts.decode(issued.response().accessToken());

        // The compatibility requirement, asserted where it matters: the subject
        // is the id of the account the response describes.
        assertEquals(issued.response().userId().toString(), decoded.getSubject());
        assertEquals("CUSTOMER", decoded.getClaimAsString(AuthJwtService.ROLE_CLAIM));
    }

    @Test
    void theSessionIdentifierIsNotReturnedInTheResponseBody() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        AuthService.IssuedSession issued = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        // It belongs in the HttpOnly cookie and nowhere a script can read it.
        assertFalse(issued.response().toString().contains(issued.sessionId()));
    }

    @Test
    void signingInAgainWithTheSameNumberReusesTheAccount() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        String first = auth.verifyPhoneOtp(PHONE, OTP, "test-agent")
                .response().userId().toString();

        // Cooldown has to pass before another code can be sent.
        redis.advance(Duration.ofSeconds(61));

        auth.sendPhoneOtp(PHONE, CLIENT);
        String second = auth.verifyPhoneOtp(PHONE, OTP, "phone")
                .response().userId().toString();

        assertEquals(first, second);
    }

    @Test
    void aWrongCodeIsRejectedAndChangesNothing() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertThrows(InvalidOtpException.class, () -> auth.verifyPhoneOtp(PHONE, "000000", "test-agent"));

        // Nothing was verified, so no session was opened either.
        assertTrue(sessions.resolve("never-issued").isEmpty());
        assertFalse(verifications.wasPhoneMarkedVerified(users.getUserByMobileNumber(PHONE)));
    }

    @Test
    void anExpiredCodeIsRejected() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);

        // Past the code's five-minute life, but inside the resend cooldown's
        // effect window on the next call.
        redis.advance(Duration.ofMinutes(6));

        assertThrows(InvalidOtpException.class, () -> auth.verifyPhoneOtp(PHONE, OTP, "test-agent"));
    }

    @Test
    void aCodeCannotBeRedeemedTwice() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        assertThrows(InvalidOtpException.class, () -> auth.verifyPhoneOtp(PHONE, OTP, "test-agent"));
    }

    @Test
    void anAccountThatMayNotSignInIsRefused() {

        User suspended = AuthTestSupport.user(
                AuthTestSupport.USER_ID, PHONE, UserRole.CUSTOMER, UserStatus.SUSPENDED);

        users.register(suspended);

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertThrows(ForbiddenException.class, () -> auth.verifyPhoneOtp(PHONE, OTP, "test-agent"));
    }

    // ------------------------------------------------------------------
    // Refreshing
    // ------------------------------------------------------------------

    @Test
    void refreshExchangesALiveSessionForANewToken() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        AuthService.IssuedSession refreshed =
                auth.refresh(signedIn.sessionId(), "test-agent");

        Jwt decoded = jwts.decode(refreshed.response().accessToken());

        assertEquals(
                signedIn.response().userId().toString(),
                decoded.getSubject());

        // The session rotated, and the caller is given the new identifier to
        // put back in the cookie.
        assertNotNull(refreshed.sessionId());
        assertTrue(sessions.resolve(refreshed.sessionId()).isPresent());
    }

    @Test
    void refreshFailsWithoutASession() {

        assertThrows(InvalidSessionException.class, () -> auth.refresh(null, "test-agent"));
        assertThrows(InvalidSessionException.class, () -> auth.refresh("", "test-agent"));
        assertThrows(InvalidSessionException.class, () -> auth.refresh("never-existed", "test-agent"));
    }

    @Test
    void refreshFailsForARevokedSession() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        auth.logout(signedIn.sessionId());

        assertThrows(
                InvalidSessionException.class,
                () -> auth.refresh(signedIn.sessionId(), "test-agent"));
    }

    @Test
    void refreshFailsForAnExpiredSession() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        redis.advance(properties.getSession().getTtl().plusSeconds(1));

        assertThrows(
                InvalidSessionException.class,
                () -> auth.refresh(signedIn.sessionId(), "test-agent"));
    }

    @Test
    void refreshFailsAndRevokesWhenTheAccountHasBeenSuspended() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        users.getUserById(signedIn.response().userId().toString())
                .setStatus(UserStatus.SUSPENDED);

        assertThrows(
                ForbiddenException.class,
                () -> auth.refresh(signedIn.sessionId(), "test-agent"));

        // A refusal the caller could simply retry past would not be a refusal.
        assertTrue(sessions.resolve(signedIn.sessionId()).isEmpty());
    }

    // ------------------------------------------------------------------
    // Signing out
    // ------------------------------------------------------------------

    @Test
    void logoutRevokesOnlyTheSessionItWasGiven() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession phone = auth.verifyPhoneOtp(PHONE, OTP, "phone");

        redis.advance(Duration.ofSeconds(61));

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession laptop = auth.verifyPhoneOtp(PHONE, OTP, "laptop");

        auth.logout(phone.sessionId());

        assertTrue(sessions.resolve(phone.sessionId()).isEmpty());
        assertTrue(sessions.resolve(laptop.sessionId()).isPresent());
    }

    @Test
    void logoutIsIdempotent() {

        registered(PHONE);

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        auth.logout(signedIn.sessionId());
        auth.logout(signedIn.sessionId());
        auth.logout(null);
        auth.logout("never-existed");
    }

    // ------------------------------------------------------------------
    // The profile
    // ------------------------------------------------------------------

    /**
     * Registers an account that has a verification record attached, which the
     * fake would not otherwise build.
     */
    private User registeredUserWithVerification() {

        User user = AuthTestSupport.activeCustomer(AuthTestSupport.USER_ID, PHONE);

        user.setVerification(UserVerification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .emailStatus(VerificationStatus.VERIFIED)
                .phoneStatus(VerificationStatus.VERIFIED)
                .panStatus(VerificationStatus.NOT_STARTED)
                .bankAccountStatus(VerificationStatus.NOT_STARTED)
                .build());

        users.register(user);

        return user;
    }

    @Test
    void signInAndRefreshResponsesCarryOnlyTheTokenAndTheUserId() {

        // Pinned by reflection because the point is the absence of what used to
        // be here: the nested user object, and its nested verification object.
        assertEquals(
                List.of("accessToken", "tokenType", "expiresInSeconds", "userId"),
                Arrays.stream(AuthResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList());
    }

    @Test
    void theProfileIncludesVerificationWhileKycIsOutstanding() {

        registeredUserWithVerification();

        UserResponse profile = auth.getProfile(AuthTestSupport.USER_ID);

        assertEquals(UUID.fromString(AuthTestSupport.USER_ID), profile.getId());
        assertEquals(Boolean.FALSE, profile.getIsKycCompleted());

        // The per-channel record is what tells the client which step is next.
        assertNotNull(profile.getVerification());
        assertEquals(VerificationStatus.VERIFIED, profile.getVerification().getPhoneStatus());
        assertEquals(VerificationStatus.NOT_STARTED, profile.getVerification().getPanStatus());
    }

    @Test
    void theProfileDropsVerificationOnceKycIsComplete() {

        User user = registeredUserWithVerification();
        user.setIsKycCompleted(true);

        UserResponse profile = auth.getProfile(AuthTestSupport.USER_ID);

        assertEquals(Boolean.TRUE, profile.getIsKycCompleted());

        // Left null here; the DTO is what removes the key from the JSON, which
        // AuthMeEndpointTest asserts. Every channel reads VERIFIED at this point,
        // so there is nothing left for the object to say.
        assertNull(profile.getVerification());

        // Everything else is still reported.
        assertEquals(PHONE, profile.getMobileNumber());
        assertEquals(UserRole.CUSTOMER, profile.getRole());
        assertEquals(UserStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void theProfileIsReadFreshRatherThanFromTheToken() {

        User user = registeredUserWithVerification();
        user.setFirstName("Renamed");
        user.setIsKycCompleted(true);

        // A name changed a moment ago is visible without waiting for the token
        // to expire.
        assertEquals("Renamed", auth.getProfile(AuthTestSupport.USER_ID).getFirstName());
    }
}
