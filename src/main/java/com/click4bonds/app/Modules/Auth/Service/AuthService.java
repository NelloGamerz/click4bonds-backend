package com.click4bonds.app.Modules.Auth.Service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Auth.Dto.AuthResponse;
import com.click4bonds.app.Modules.Auth.Dto.SignupRequest;
import com.click4bonds.app.Modules.Auth.Exception.InvalidSessionException;
import com.click4bonds.app.Modules.Auth.Exception.OtpRateLimitedException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.IdentifierNormalizer;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.Sms.service.SmsService;
import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Dto.UserVerificationResponse;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;
import com.click4bonds.app.Modules.User.Service.UserService;
import com.click4bonds.app.Modules.User.Service.VerificationService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Signing up and signing in with a phone number.
 *
 * <p>Ownership of the number is the whole of the identity proof: a code is
 * delivered to it by SMS, and redeeming that code is what opens a session.
 * Nothing else — no password, no external identity provider — is consulted.</p>
 *
 * <p>An account is created by {@link #signup}, and only there. Redeeming a code
 * cannot conjure one: a number with no account behind it is refused and told to
 * sign up, so the profile a sign-up collected is never bypassed. That is also
 * why the two endpoints report the same thing about an unknown number — an
 * account either exists because somebody signed up, or it does not.</p>
 *
 * <p>The OTP mechanics are entirely the OTP module's: generation, hashing,
 * expiry, the attempt limit and the resend cooldown all live there and are
 * reused here rather than reimplemented. What this class adds is the part that
 * knows about accounts: creating one from a sign-up form, finding it from a
 * proof of ownership, refusing one that may not sign in, and issuing the token
 * and session that follow.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Reported after a sign-up that also had a code sent to it. */
    public static final String SIGNUP_COMPLETE =
            "Sign-up successful. A verification code has been sent to your phone.";

    private final OtpService otpService;
    private final SmsService smsService;
    private final UserService userService;
    private final VerificationService verificationService;
    private final AuthJwtService authJwtService;
    private final AuthSessionService authSessionService;
    private final AuthProperties authProperties;
    private final RedisService redisService;

    /** A session that was opened, and the token issued for it. */
    public record IssuedSession(String sessionId, AuthResponse response) {
    }

    /**
     * Opens an account from a sign-up form and sends it a code.
     *
     * <p>The profile is written before the code is issued rather than after it
     * is redeemed, which is what makes the number the identity it signs in
     * with: the account exists from this call onwards, and
     * {@link #verifyPhoneOtp} only ever looks one up.</p>
     *
     * <p>The two steps are deliberately not one transaction. Issuing the code
     * reaches an SMS provider, and holding a database transaction open across
     * that call would keep a connection tied up for the length of somebody
     * else's HTTP request. So the write is committed first and the code is sent
     * afterwards: if delivery fails, the account survives and its owner can ask
     * for another code at {@code /auth/phone/send-otp} with the profile they
     * already filled in, rather than having to enter it again.</p>
     *
     * <p>The number is checked before anything is written. Two sign-ups racing
     * for the same number are still separated by the unique index on it — the
     * loser is refused by the database rather than by this check — but the
     * check is what turns the ordinary case into a clear answer.</p>
     *
     * @param request       the sign-up form
     * @param clientAddress address the request came from, for the OTP rate limit
     * @return a message that says a code was sent, and nothing about the code
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ConflictException
     *         when the number already has an account
     * @throws OtpRateLimitedException when this address has asked too often
     */
    public VerificationResponse signup(SignupRequest request, String clientAddress) {

        String normalized = IdentifierNormalizer.normalize(OtpType.SMS, request.mobileNumber());

        if (userService.isMobileNumberClaimed(normalized)) {
            throw new ConflictException(
                    "This phone number is already registered. Please sign in instead.");
        }

        // Signing up establishes the number, and proving it is the next thing
        // the account is asked to do — which is why onboarding starts at the
        // phone step rather than at the email step that follows a plain
        // sign-in. Nothing else here is defaulted: the profile comes from the
        // form, and the account opens as an active customer with no email
        // address, because no address was asked for.
        User user = userService.createUser(User.builder()
                .mobileNumber(normalized)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .ageRange(request.ageRange())
                .userType(request.userType())
                .preferredCommunicationLanguage(request.preferredCommunicationLanguage())
                .whatsappCommunicationConsent(request.whatsappCommunicationConsent())
                .termsAccepted(request.termsAccepted())
                .onboardingStep(OnboardingStep.PHONE_VERIFICATION)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        log.info("Sign-up opened the account for user {}", user.getId());

        // The account exists by now, so this cannot answer "please sign up" to
        // the caller who just did.
        sendPhoneOtp(normalized, clientAddress);

        return new VerificationResponse(SIGNUP_COMPLETE);
    }

    /**
     * Sends a sign-in code to a phone number.
     *
     * <p>Only a number that already has an account is sent to. One that does
     * not is refused and told to sign up, because there is nothing for it to
     * sign in to — a code would prove ownership of a number that leads nowhere,
     * and the caller would find that out one step later instead of here.</p>
     *
     * <p>The cost of that answer is that this endpoint reports whether a given
     * number is registered. The rate limit is what keeps it from being a useful
     * enumeration oracle: the allowance is spent before the lookup, so sweeping
     * a range of numbers is throttled long before it is worth doing, and a
     * caller cannot probe for free by asking and then hanging up.</p>
     *
     * @param phone         number to send to, in any accepted spelling
     * @param clientAddress address the request came from, for rate limiting
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException
     *                                    when no account signs in with this number
     * @throws OtpRateLimitedException    when this address has asked too often
     * @throws com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException
     *                                    when this number was asked for too recently
     */
    public void sendPhoneOtp(String phone, String clientAddress) {

        // Normalise before consuming any rate-limit allowance: malformed input
        // costs nothing to reject and must not spend the caller's budget.
        String normalized = IdentifierNormalizer.normalize(OtpType.SMS, phone);

        // Also before the lookup below, so a sweep across numbers spends the
        // allowance rather than reading the answer out of it.
        enforceOtpRateLimit(clientAddress);

        userService.getUserByMobileNumber(normalized);

        String otp = otpService.generateOtp(OtpType.SMS, normalized);

        // The only place a code leaves this application. It is never logged and
        // never returned in a response.
        smsService.sendOtp(normalized, otp);
    }

    /**
     * Redeems a sign-in code and opens a session.
     *
     * <p>The order matters. The code is verified first, so nothing downstream
     * runs on an unproven number — a wrong, expired or exhausted code leaves
     * the account untouched. Only then is the account looked up, checked, and
     * given a session.</p>
     *
     * <p>Looked up, not created: sign-up is the only way an account comes into
     * existence, and a number that never went through it is refused here with
     * the same answer {@link #sendPhoneOtp} gives.</p>
     *
     * @param phone     number the code was sent to
     * @param otp       submitted code
     * @param userAgent client description, recorded against the session
     * @return the new session and the access token issued for it
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException
     *         when no account signs in with this number
     */
    @Transactional
    public IssuedSession verifyPhoneOtp(String phone, String otp, String userAgent) {

        String normalized = IdentifierNormalizer.normalize(OtpType.SMS, phone);

        // Consumes the code: a success here cannot be replayed.
        otpService.verifyOtp(OtpType.SMS, normalized, otp);

        User user = userService.getUserByMobileNumber(normalized);

        assertCanSignIn(user);

        // Signing in proves the number exactly as the verification endpoint
        // does, so the account's verification state is brought in line rather
        // than left claiming an unverified phone.
        verificationService.markPhoneVerified(user);

        return startSession(user, userAgent);
    }

    /**
     * Exchanges a session for a fresh access token.
     *
     * <p>The session is the long-lived credential; the access token is not. A
     * client whose token has expired presents the cookie it already holds and
     * receives a new one, without the user being asked to sign in again.</p>
     *
     * <p>The account is re-checked on every refresh rather than trusted from
     * the session that was created days ago: an account suspended, deleted or
     * otherwise barred since then must not be able to keep minting tokens. When
     * that is what is found, the session is destroyed as well, so the refusal
     * is not something the caller can retry their way past.</p>
     *
     * @param sessionId identifier from the request cookie, may be null
     * @param userAgent client description, recorded if the session rotates
     * @return the live session identifier and a new access token
     * @throws InvalidSessionException when the session is unknown, expired or revoked
     */
    @Transactional
    public IssuedSession refresh(String sessionId, String userAgent) {

        AuthSessionService.ResolvedSession resolved = authSessionService
                .refresh(sessionId, userAgent)
                .orElseThrow(InvalidSessionException::new);

        User user;

        try {
            user = userService.getUser(resolved.session().userId());

        } catch (ResponseStatusException ex) {
            // The account is gone. The session outlived it, so it goes too.
            authSessionService.revoke(resolved.sessionId());
            log.info("Session discarded: its account no longer exists");
            throw new InvalidSessionException();
        }

        try {
            assertCanSignIn(user);

        } catch (ForbiddenException ex) {
            authSessionService.revoke(resolved.sessionId());
            log.info("Session discarded for user {}: not permitted to sign in", user.getId());
            throw ex;
        }

        String accessToken = authJwtService.createAccessToken(user);

        return new IssuedSession(
                resolved.sessionId(),
                buildResponse(accessToken, user));
    }

    /**
     * Ends the session behind a cookie.
     *
     * <p>Idempotent by design: a logout that arrives after the session already
     * expired, or twice from the same tab, is not an error. The caller asked to
     * be signed out, and they are.</p>
     *
     * <p>Only this session is revoked. Other devices signed in to the same
     * account keep working, which is what signing out on one device is expected
     * to mean.</p>
     *
     * @param sessionId identifier from the request cookie, may be null
     */
    public void logout(String sessionId) {
        authSessionService.revoke(sessionId);
    }

    /**
     * Opens a session and issues the access token that goes with it.
     */
    private IssuedSession startSession(User user, String userAgent) {

        String sessionId = authSessionService.create(user.getId(), userAgent);
        String accessToken = authJwtService.createAccessToken(user);

        log.info("User {} signed in", user.getId());

        return new IssuedSession(sessionId, buildResponse(accessToken, user));
    }

    /**
     * Refuses an account that is not allowed to sign in.
     *
     * <p>Suspended and deleted accounts are turned away here rather than being
     * allowed a session that would then be rejected on every request.</p>
     */
    private void assertCanSignIn(User user) {

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("This account cannot sign in");
        }
    }

    /**
     * Counts this request against the client address's allowance.
     *
     * <p>The count is taken before the code is generated, so a request that
     * fails for any other reason still counts. Counting only successes would
     * let a caller retry freely, which is the opposite of what a rate limit is
     * for.</p>
     *
     * <p>A limit of zero or less disables the check, which is how tests and a
     * local run avoid needing Redis for it.</p>
     */
    private void enforceOtpRateLimit(String clientAddress) {

        AuthProperties.Otp otp = authProperties.getOtp();

        if (otp.getMaxRequestsPerWindow() <= 0) {
            return;
        }

        String address = (clientAddress == null || clientAddress.isBlank())
                ? "unknown"
                : clientAddress;

        long count = redisService.increment(
                AuthKeyFactory.otpRateLimitKey(address),
                otp.getRateLimitWindow());

        if (count > otp.getMaxRequestsPerWindow()) {
            log.warn("OTP request rate limit reached for a client address");
            throw new OtpRateLimitedException();
        }
    }

    /**
     * The token, its type, its lifetime, and who it belongs to.
     *
     * <p>Deliberately no profile. A client that has just signed in needs the
     * wherewithal to make requests, and can fetch the profile at
     * {@link #getProfile} when it has a screen to fill.</p>
     */
    private AuthResponse buildResponse(String accessToken, User user) {

        return new AuthResponse(
                accessToken,
                authProperties.getJwt().getAccessTokenTtl().toSeconds(),
                user.getId());
    }

    /**
     * The signed-in user's full profile.
     *
     * <p>Behind {@code GET /auth/me}, which requires an access token. The
     * account is looked up fresh rather than projected from the token, so the
     * profile reflects the database as it is now — a name changed or a
     * verification completed a moment ago is visible without waiting for the
     * token to expire.</p>
     *
     * <p>Read inside a transaction because the verification association is
     * lazy and would not survive leaving one.</p>
     *
     * @param userId subject of the authenticated token
     * @return the profile, with the verification record included only while KYC
     *         is still outstanding
     */
    @Transactional
    public UserResponse getProfile(String userId) {
        return toUserResponse(userService.getUserById(userId));
    }

    /**
     * Projects an account into the shape the API returns.
     *
     * <p>The verification record is dropped once KYC is complete: every channel
     * in it reads {@code VERIFIED} by then, so it says nothing a caller can act
     * on. The field is left null and the DTO omits it from the JSON rather than
     * sending an object full of identical values.</p>
     */
    private UserResponse toUserResponse(User user) {

        boolean kycCompleted = Boolean.TRUE.equals(user.getIsKycCompleted());

        UserVerification verification = kycCompleted ? null : user.getVerification();

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profileImage(user.getProfileImage())
                .onboardingStep(user.getOnboardingStep())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .isKycCompleted(user.getIsKycCompleted())
                .verification(
                        verification == null
                                ? null
                                : UserVerificationResponse.builder()
                                        .id(verification.getId())
                                        .emailStatus(verification.getEmailStatus())
                                        .phoneStatus(verification.getPhoneStatus())
                                        .panStatus(verification.getPanStatus())
                                        .bankAccountStatus(verification.getBankAccountStatus())
                                        .createdAt(verification.getCreatedAt())
                                        .updatedAt(verification.getUpdatedAt())
                                        .build())
                .build();
    }
}
