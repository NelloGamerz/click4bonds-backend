package com.click4bonds.app.Modules.User.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.Email.Config.EmailProperties;
import com.click4bonds.app.Modules.Email.Model.EmailRequest;
import com.click4bonds.app.Modules.Email.Provider.EmailProvider;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderFactory;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderType;
import com.click4bonds.app.Modules.Email.Service.EmailService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Generator.OtpGenerator;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.OtpHasher;
import com.click4bonds.app.Modules.OTP.Service.OtpKeyFactory;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * Wiring for the verification service tests.
 *
 * <p>Everything below the application service is real: a genuine
 * {@link OtpService} over an in-memory Redis, and a genuine {@link EmailService}
 * over a provider that records what it was asked to deliver. Only the user
 * side is doubled, so the tests can drive account state directly.</p>
 */
final class VerificationTestSupport {

    /**
     * The signed-in user's identifier, as the token subject carries it: the
     * string form of {@code User.id}. Tests pass this where a real request
     * would pass {@code jwt.getSubject()}.
     */
    static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    static final String EMAIL = "user@example.com";
    static final String PHONE = "+919876543210";
    static final String OTP = "483920";

    static final String EMAIL_KEY = OtpKeyFactory.otpKey(OtpType.EMAIL, EMAIL);
    static final String SMS_KEY = OtpKeyFactory.otpKey(OtpType.SMS, PHONE);

    private VerificationTestSupport() {
    }

    static OtpProperties otpProperties() {

        OtpProperties properties = new OtpProperties();
        properties.setLength(6);
        properties.setExpiryMinutes(15);
        properties.setMaxAttempts(5);
        properties.setResendCooldownSeconds(60);
        properties.setHashSecret("test-only-otp-hash-secret");

        return properties;
    }

    /** Emits one known code, so a test can submit it. */
    static OtpGenerator fixedGenerator() {
        return () -> OTP;
    }

    static OtpService otpService(InMemoryRedisService redis, OtpProperties properties) {
        return new OtpService(redis, fixedGenerator(), new OtpHasher(properties), properties);
    }

    static EmailService emailService(CapturingEmailProvider provider) {

        EmailProperties properties = new EmailProperties();
        properties.setProvider("resend");

        return new EmailService(
                new EmailProviderFactory(List.of(provider), properties),
                "http://localhost:3000");
    }

    /**
     * @param userId identifier string, which is also what the account's
     *               {@code id} becomes — the two are the same thing, which is
     *               what makes the token subject resolvable
     */
    static User user(String userId, String email, String mobileNumber) {

        return User.builder()
                .id(UUID.fromString(userId))
                .email(email)
                .mobileNumber(mobileNumber)
                .onboardingStep(OnboardingStep.EMAIL_VERIFICATION)
                .build();
    }

    /** Records every email the application asks to have delivered. */
    static final class CapturingEmailProvider implements EmailProvider {

        private final List<EmailRequest> sent = new ArrayList<>();

        @Override
        public EmailProviderType type() {
            return EmailProviderType.RESEND;
        }

        @Override
        public void send(EmailRequest request) {
            sent.add(request);
        }

        List<EmailRequest> sent() {
            return sent;
        }

        EmailRequest lastSent() {

            if (sent.isEmpty()) {
                throw new AssertionError("No email was sent");
            }

            return sent.get(sent.size() - 1);
        }
    }

    /**
     * In-memory stand-in for {@link UserService} that really mutates the user it
     * is given, so a test can read the resulting onboarding step off the entity.
     */
    static final class FakeUserService extends UserService {

        private final Map<String, User> byId = new HashMap<>();
        private final Set<String> claimedMobileNumbers = new HashSet<>();
        private final Set<String> claimedEmails = new HashSet<>();

        FakeUserService() {
            super(null, null);
        }

        FakeUserService register(User user) {

            byId.put(user.getId().toString(), user);

            if (user.getMobileNumber() != null) {
                claimedMobileNumbers.add(user.getMobileNumber());
            }

            if (user.getEmail() != null) {
                claimedEmails.add(user.getEmail());
            }

            return this;
        }

        /** Marks a number as already owned by some other account. */
        FakeUserService alreadyTakenBySomeoneElse(String mobileNumber) {

            claimedMobileNumbers.add(mobileNumber);

            return this;
        }

        /** Marks an address as already owned by some other account. */
        FakeUserService emailTakenBySomeoneElse(String email) {

            claimedEmails.add(email);

            return this;
        }

        @Override
        public User getUserById(String userId) {

            User user = byId.get(userId);

            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }

            return user;
        }

        @Override
        public void updateEmail(User user, String email) {

            user.setEmail(email);
            claimedEmails.add(email);
        }

        @Override
        public boolean isEmailClaimed(String email) {
            return claimedEmails.contains(email);
        }

        @Override
        public void updateOnboardingStep(User user, OnboardingStep onboardingStep) {
            user.setOnboardingStep(onboardingStep);
        }

        @Override
        public void updateMobileNumber(User user, String mobileNumber) {

            user.setMobileNumber(mobileNumber);
            claimedMobileNumbers.add(mobileNumber);
        }

        @Override
        public boolean isMobileNumberClaimed(String mobileNumber) {
            return claimedMobileNumbers.contains(mobileNumber);
        }
    }

    /**
     * In-memory stand-in for {@link UserVerificationService} holding a single
     * verification record per user.
     */
    static final class FakeUserVerificationService extends UserVerificationService {

        private final Map<UUID, UserVerification> byUserId = new HashMap<>();

        FakeUserVerificationService() {
            super(null);
        }

        UserVerification register(User user) {

            UserVerification verification = UserVerification.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .emailStatus(VerificationStatus.NOT_STARTED)
                    .phoneStatus(VerificationStatus.NOT_STARTED)
                    .panStatus(VerificationStatus.NOT_STARTED)
                    .bankAccountStatus(VerificationStatus.NOT_STARTED)
                    .build();

            byUserId.put(user.getId(), verification);

            return verification;
        }

        @Override
        public UserVerification getVerification(User user) {

            UserVerification verification = byUserId.get(user.getId());

            if (verification == null) {
                throw new IllegalStateException(
                        "Verification record not found for user " + user.getId());
            }

            return verification;
        }

        @Override
        public void updateEmailStatus(User user, VerificationStatus status) {
            getVerification(user).setEmailStatus(status);
        }

        @Override
        public void updatePhoneStatus(User user, VerificationStatus status) {
            getVerification(user).setPhoneStatus(status);
        }
    }
}
