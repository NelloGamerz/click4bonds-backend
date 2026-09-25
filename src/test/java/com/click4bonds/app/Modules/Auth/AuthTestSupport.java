package com.click4bonds.app.Modules.Auth;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Config.SecurityConfig;
import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Sms.service.SmsService;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;
import com.click4bonds.app.Modules.User.Service.VerificationService;

/**
 * Shared wiring for the authentication tests.
 *
 * <p>Real components wherever they are cheap and meaningful — the JWT service,
 * the session service, the cookie service and the OTP service are all the
 * genuine article, over an in-memory Redis. Only the parts that would reach a
 * database or an SMS gateway are doubled, and those doubles record what they
 * were asked to do so a test can assert on it.</p>
 */
public final class AuthTestSupport {

    /** HS256 needs 32 bytes; this is comfortably past it and is not a secret. */
    public static final String JWT_SECRET = "test-only-jwt-signing-secret-that-is-long-enough";

    public static final String PHONE = "+919876543210";

    public static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private AuthTestSupport() {
    }

    /** Settings a test can rely on, with lifetimes short enough to reason about. */
    public static AuthProperties properties() {

        AuthProperties properties = new AuthProperties();

        properties.getJwt().setSecret(JWT_SECRET);
        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));

        properties.getSession().setTtl(Duration.ofDays(30));
        properties.getSession().setCookieName("session");
        properties.getSession().setCookieSecure(false);
        properties.getSession().setCookieSameSite("Lax");
        properties.getSession().setCookieDomain(null);

        properties.getOtp().setMaxRequestsPerWindow(1000);
        properties.getOtp().setRateLimitWindow(Duration.ofMinutes(15));

        return properties;
    }

    /**
     * The real converter the application uses, so a test asserting on
     * authorities is asserting on production wiring rather than a copy of it.
     */
    public static org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
            authenticationConverter() {

        return new SecurityConfig().jwtAuthenticationConverter();
    }

    public static User user(String userId, String mobileNumber, UserRole role, UserStatus status) {

        return User.builder()
                .id(UUID.fromString(userId))
                .mobileNumber(mobileNumber)
                .role(role)
                .status(status)
                .onboardingStep(OnboardingStep.EMAIL_VERIFICATION)
                .build();
    }

    public static User activeCustomer(String userId, String mobileNumber) {
        return user(userId, mobileNumber, UserRole.CUSTOMER, UserStatus.ACTIVE);
    }

    /**
     * A clock a test can move forward, so token expiry can be exercised
     * exactly rather than by waiting for it.
     */
    public static final class MutableClock extends java.time.Clock {

        private Instant now;

        /**
         * Starts at the real time, so anything validating against the system
         * clock — a decoder built without this clock, say — agrees with it.
         */
        public MutableClock() {
            this(Instant.now());
        }

        public MutableClock(Instant start) {
            this.now = start;
        }

        /**
         * A clock an hour behind, so a token minted through it is already
         * expired by the time anything on the real clock looks at it.
         */
        public static MutableClock inThePast() {
            return new MutableClock(Instant.now().minus(Duration.ofHours(1)));
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        public void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    /** Records OTP messages instead of sending them. */
    public static final class RecordingSmsService extends SmsService {

        private final Map<String, String> sent = new HashMap<>();

        public RecordingSmsService() {
            super(null, "http://localhost/never-called", "ukey", "sender", "Your code is <arg1>");
        }

        @Override
        public void sendOtp(String phone, String otp) {
            sent.put(phone, otp);
        }

        /** @return the code last sent to {@code phone}, or null */
        public String lastOtpFor(String phone) {
            return sent.get(phone);
        }

        public int sentCount() {
            return sent.size();
        }
    }

    /**
     * In-memory stand-in for {@link UserService}.
     *
     * <p>Implements the rule the real one does — a number resolves to the
     * account that was signed up for it, and to nothing else — so a test can
     * assert on identity across two sign-ins and on the refusal of a number
     * that was never signed up.</p>
     */
    public static final class FakeUserService extends UserService {

        private final Map<UUID, User> byId = new HashMap<>();
        private final Map<String, User> byMobileNumber = new HashMap<>();

        public FakeUserService() {
            super(null, null);
        }

        public FakeUserService register(User user) {

            byId.put(user.getId(), user);

            if (user.getMobileNumber() != null) {
                byMobileNumber.put(user.getMobileNumber(), user);
            }

            return this;
        }

        @Override
        public User createUser(User user) {

            // The real one gets its identifier from the database, which this
            // double has no equivalent of.
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }

            register(user);

            return user;
        }

        @Override
        public User getUserByMobileNumber(String mobileNumber) {

            User user = byMobileNumber.get(mobileNumber);

            if (user == null) {
                throw new ResourceNotFoundException(SIGNUP_REQUIRED);
            }

            return user;
        }

        @Override
        public User getUserById(String userId) {

            if (userId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }

            try {
                return getUser(UUID.fromString(userId));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
        }

        @Override
        public User getUser(UUID userId) {

            User user = byId.get(userId);

            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }

            return user;
        }

        @Override
        public boolean isMobileNumberClaimed(String mobileNumber) {
            return byMobileNumber.containsKey(mobileNumber);
        }

        @Override
        public void updateMobileNumber(User user, String mobileNumber) {

            user.setMobileNumber(mobileNumber);
            byMobileNumber.put(mobileNumber, user);
        }

        @Override
        public void updateOnboardingStep(User user, OnboardingStep onboardingStep) {
            user.setOnboardingStep(onboardingStep);
        }
    }

    /**
     * In-memory stand-in for {@link VerificationService} that records the call
     * without needing an OTP, an email provider or a verification record.
     */
    public static final class FakeVerificationService extends VerificationService {

        private final Set<UUID> phoneVerified = new HashSet<>();

        public FakeVerificationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void markPhoneVerified(User user) {

            phoneVerified.add(user.getId());

            // Mirrors the real rule: a phone-first account starts at the email
            // step, so there is nothing to advance past here.
            if (user.getOnboardingStep() == OnboardingStep.PHONE_VERIFICATION) {
                user.setOnboardingStep(OnboardingStep.PAN_VERIFICATION);
            }
        }

        public boolean wasPhoneMarkedVerified(User user) {
            return phoneVerified.contains(user.getId());
        }
    }
}
