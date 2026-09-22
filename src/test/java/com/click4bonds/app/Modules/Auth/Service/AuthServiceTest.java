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
import com.click4bonds.app.Modules.Auth.Exception.InvalidSessionException;
import com.click4bonds.app.Modules.Auth.Exception.OtpRateLimitedException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
import com.click4bonds.app.Modules.OTP.Service.OtpHasher;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
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

        OtpService otpService = new OtpService(
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

    // ------------------------------------------------------------------
    // Sending a code
    // ------------------------------------------------------------------

    @Test
    void sendingACodeDeliversItBySms() {

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertEquals(OTP, sms.lastOtpFor(PHONE));
    }

    @Test
    void sendingACodeBehavesTheSameWhetherOrNotTheNumberIsRegistered() {

        String registered = "+919000000001";
        String unknown = "+919000000002";

        users.register(AuthTestSupport.activeCustomer(AuthTestSupport.USER_ID, registered));

        // Both succeed, and both get a code. Nothing in the outcome says which
        // number already had an account.
        auth.sendPhoneOtp(registered, CLIENT);
        auth.sendPhoneOtp(unknown, CLIENT);

        assertNotNull(sms.lastOtpFor(registered));
        assertNotNull(sms.lastOtpFor(unknown));
    }

    @Test
    void issuingACodeCreatesNoAccount() {

        auth.sendPhoneOtp(PHONE, CLIENT);

        // A code proves nothing, so nothing is created on the strength of it.
        assertFalse(users.isMobileNumberClaimed(PHONE));
    }

    @Test
    void aSecondCodeForTheSameNumberIsRefusedUntilTheCooldownElapses() {

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertThrows(OtpResendCooldownException.class, () -> auth.sendPhoneOtp(PHONE, CLIENT));
    }

    @Test
    void requestsFromOneAddressAreRateLimited() {

        properties.getOtp().setMaxRequestsPerWindow(2);

        auth.sendPhoneOtp("+919000000010", CLIENT);
        auth.sendPhoneOtp("+919000000011", CLIENT);

        // The per-number cooldown does not stop a sweep across many numbers;
        // this is the limit that does.
        assertThrows(OtpRateLimitedException.class, () -> auth.sendPhoneOtp("+919000000012", CLIENT));
    }

    @Test
    void anotherAddressHasItsOwnAllowance() {

        properties.getOtp().setMaxRequestsPerWindow(1);

        auth.sendPhoneOtp("+919000000020", CLIENT);

        assertThrows(OtpRateLimitedException.class, () -> auth.sendPhoneOtp("+919000000021", CLIENT));

        // One noisy client must not lock everybody else out.
        auth.sendPhoneOtp("+919000000022", "198.51.100.9");
    }

    @Test
    void malformedNumbersDoNotSpendTheAllowance() {

        properties.getOtp().setMaxRequestsPerWindow(1);

        assertThrows(
                com.click4bonds.app.Modules.Common.Exceptions.BadRequestException.class,
                () -> auth.sendPhoneOtp("not-a-number", CLIENT));

        auth.sendPhoneOtp(PHONE, CLIENT);
    }

    // ------------------------------------------------------------------
    // Redeeming a code
    // ------------------------------------------------------------------

    @Test
    void aValidCodeCreatesTheAccountAndOpensASession() {

        auth.sendPhoneOtp(PHONE, CLIENT);

        AuthService.IssuedSession issued = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        User created = users.getUserById(issued.response().userId().toString());

        assertEquals(PHONE, created.getMobileNumber());
        assertEquals(UserRole.CUSTOMER, created.getRole());
        assertEquals(UserStatus.ACTIVE, created.getStatus());

        // The response describes that same account.
        assertEquals(created.getId(), issued.response().userId());

        // The number was proven by signing in, so the record says so.
        assertTrue(verifications.wasPhoneMarkedVerified(created));

        // A session exists under the identifier handed back for the cookie.
        assertTrue(sessions.resolve(issued.sessionId()).isPresent());
    }

    @Test
    void theAccessTokenNamesTheUserByIdentifier() {

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

        auth.sendPhoneOtp(PHONE, CLIENT);

        AuthService.IssuedSession issued = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        // It belongs in the HttpOnly cookie and nowhere a script can read it.
        assertFalse(issued.response().toString().contains(issued.sessionId()));
    }

    @Test
    void signingInAgainWithTheSameNumberReusesTheAccount() {

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
    void aWrongCodeIsRejectedAndCreatesNothing() {

        auth.sendPhoneOtp(PHONE, CLIENT);

        assertThrows(InvalidOtpException.class, () -> auth.verifyPhoneOtp(PHONE, "000000", "test-agent"));

        // Nothing was created, and nothing was verified.
        assertFalse(users.isMobileNumberClaimed(PHONE));
    }

    @Test
    void anExpiredCodeIsRejected() {

        auth.sendPhoneOtp(PHONE, CLIENT);

        // Past the code's five-minute life, but inside the resend cooldown's
        // effect window on the next call.
        redis.advance(Duration.ofMinutes(6));

        assertThrows(InvalidOtpException.class, () -> auth.verifyPhoneOtp(PHONE, OTP, "test-agent"));
    }

    @Test
    void aCodeCannotBeRedeemedTwice() {

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

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        auth.logout(signedIn.sessionId());

        assertThrows(
                InvalidSessionException.class,
                () -> auth.refresh(signedIn.sessionId(), "test-agent"));
    }

    @Test
    void refreshFailsForAnExpiredSession() {

        auth.sendPhoneOtp(PHONE, CLIENT);
        AuthService.IssuedSession signedIn = auth.verifyPhoneOtp(PHONE, OTP, "test-agent");

        redis.advance(properties.getSession().getTtl().plusSeconds(1));

        assertThrows(
                InvalidSessionException.class,
                () -> auth.refresh(signedIn.sessionId(), "test-agent"));
    }

    @Test
    void refreshFailsAndRevokesWhenTheAccountHasBeenSuspended() {

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
