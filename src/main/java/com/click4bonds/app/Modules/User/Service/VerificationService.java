package com.click4bonds.app.Modules.User.Service;

import com.click4bonds.app.Modules.Sms.service.SmsService;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Email.Service.EmailService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.IdentifierNormalizer;
import com.click4bonds.app.Modules.OTP.Service.OtpService;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service for the email and phone verification flows.
 *
 * <p>This is the only place where the OTP, email and user domains meet. The OTP
 * module stays a generic code issuer that knows nothing about users, email or
 * onboarding; {@link UserVerificationService} stays a persistence service for
 * the verification record. Deciding <em>whose</em> identifier a request refers
 * to, handing the code to the delivery layer, and moving the account forward
 * afterwards is orchestration, and it lives here.</p>
 *
 * <p>An issued code is never evidence of anything. Sending an OTP leaves every
 * verification status untouched; only a successful
 * {@link OtpService#verifyOtp} call changes state, and it does so in a single
 * database transaction.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    public static final String EMAIL_OTP_SENT =
            "Verification OTP sent successfully.";

    public static final String EMAIL_ALREADY_VERIFIED =
            "Email is already verified.";

    public static final String EMAIL_VERIFIED =
            "Email verified successfully.";

    public static final String PHONE_OTP_GENERATED =
            "Verification OTP generated successfully.";

    public static final String PHONE_ALREADY_VERIFIED =
            "Phone number is already verified.";

    public static final String PHONE_VERIFIED =
            "Phone number verified successfully.";

    private final OtpService otpService;
    private final EmailService emailService;
    private final UserService userService;
    private final UserVerificationService userVerificationService;
    private final OtpProperties otpProperties;
    private final SmsService smsService;

    /**
     * Issues an email verification code and delivers it.
     *
     * <p>The address must be the one the account owns; an OTP is therefore
     * never issued for an address the caller happens to type. The code is sent
     * through the existing email module — nothing here talks to a provider.</p>
     *
     * <p>Deliberately not transactional: it performs no database write, and the
     * transaction would otherwise stay open across the provider's HTTP call.</p>
     *
     * @param userId authenticated user identifier; the token subject, which is User.id
     * @param email       address to verify
     * @return a message that says nothing about the code itself
     */
    public VerificationResponse sendEmailOtp(String userId, String email) {

        User user = userService.getUserById(userId);
        String normalizedEmail = IdentifierNormalizer.normalize(OtpType.EMAIL, email);

        assertEmailCanBeVerified(user, normalizedEmail);

        UserVerification verification = userVerificationService.getVerification(user);

        if (verification.getEmailStatus() == VerificationStatus.VERIFIED) {
            return new VerificationResponse(EMAIL_ALREADY_VERIFIED);
        }

        String otp = otpService.generateOtp(OtpType.EMAIL, normalizedEmail);

        // The configured validity is handed to the template so the mail cannot
        // promise a window the code does not actually have. The code is never
        // logged.
        emailService.sendOtp(normalizedEmail, otp, otpProperties.getExpiryMinutes());

        return new VerificationResponse(EMAIL_OTP_SENT);
    }

    /**
     * Verifies a submitted email code and records the result.
     *
     * <p>The database is only touched once {@link OtpService#verifyOtp} has
     * accepted the code, so a wrong, expired or exhausted code leaves both the
     * verification record and the onboarding step exactly as they were.</p>
     *
     * @param userId authenticated user identifier; the token subject, which is User.id
     * @param email       address being verified
     * @param otp         submitted code
     * @return a message that never echoes the code
     */
    @Transactional
    public VerificationResponse verifyEmailOtp(String userId, String email, String otp) {

        User user = userService.getUserById(userId);
        String normalizedEmail = IdentifierNormalizer.normalize(OtpType.EMAIL, email);

        assertEmailCanBeVerified(user, normalizedEmail);

        // Throws when the code is wrong, unknown, expired, already redeemed or
        // out of attempts — so nothing below runs on anything but a real code.
        otpService.verifyOtp(OtpType.EMAIL, normalizedEmail, otp);

        // Bound here, after proof, and never before — the same rule the phone
        // step follows. An account that signed up by phone has no address yet,
        // so this is where it acquires one.
        if (user.getEmail() == null) {
            userService.updateEmail(user, normalizedEmail);
        }

        userVerificationService.updateEmailStatus(user, VerificationStatus.VERIFIED);
        advanceOnboarding(user, OnboardingStep.EMAIL_VERIFICATION, OnboardingStep.PHONE_VERIFICATION);

        log.info("Email verified for user {}", user.getId());

        return new VerificationResponse(EMAIL_VERIFIED);
    }

    /**
     * Issues a phone verification code.
     *
     * <p>SMS delivery is not implemented yet: the code is generated and stored
     * by the OTP module and nothing is sent. It is still a secret, so the
     * response says no more than that the code was generated.</p>
     *
     * @param userId authenticated user identifier; the token subject, which is User.id
     * @param phone       number to verify
     * @return a message that never echoes the code
     */
    public VerificationResponse sendPhoneOtp(String userId, String phone) {

        User user = userService.getUserById(userId);
        String normalizedPhone = IdentifierNormalizer.normalize(OtpType.SMS, phone);

        assertPhoneCanBeVerified(user, normalizedPhone);

        UserVerification verification = userVerificationService.getVerification(user);

        if (verification.getPhoneStatus() == VerificationStatus.VERIFIED) {
            return new VerificationResponse(PHONE_ALREADY_VERIFIED);
        }

        // Issuing a code proves nothing: the statuses are left untouched.
//        otpService.generateOtp(OtpType.SMS, normalizedPhone);
        String otp = otpService.generateOtp(OtpType.SMS, normalizedPhone);
        smsService.sendOtp(normalizedPhone, otp);

        return new VerificationResponse(PHONE_OTP_GENERATED);
    }

    /**
     * Verifies a submitted phone code and records the result.
     *
     * <p>Ownership of the number is only accepted once the code has been
     * accepted: a number the account did not already own is bound to it here,
     * after proof, and never before.</p>
     *
     * @param userId authenticated user identifier; the token subject, which is User.id
     * @param phone       number being verified
     * @param otp         submitted code
     * @return a message that never echoes the code
     */
    @Transactional
    public VerificationResponse verifyPhoneOtp(String userId, String phone, String otp) {

        User user = userService.getUserById(userId);
        String normalizedPhone = IdentifierNormalizer.normalize(OtpType.SMS, phone);

        assertPhoneCanBeVerified(user, normalizedPhone);

        // Throws when the code is wrong, unknown, expired, already redeemed or
        // out of attempts — so the number is never bound on a failed attempt.
        otpService.verifyOtp(OtpType.SMS, normalizedPhone, otp);

        if (user.getMobileNumber() == null) {
            userService.updateMobileNumber(user, normalizedPhone);
        }

        userVerificationService.updatePhoneStatus(user, VerificationStatus.VERIFIED);
        advanceOnboarding(user, OnboardingStep.PHONE_VERIFICATION, OnboardingStep.PAN_VERIFICATION);

        log.info("Phone verified for user {}", user.getId());

        return new VerificationResponse(PHONE_VERIFIED);
    }

    /**
     * Records that ownership of an account's number has been proven, and moves
     * onboarding on if that was the step it was waiting on.
     *
     * <p>Exists so the sign-in flow can reuse this rule rather than restate it.
     * Signing in by phone proves the number every bit as much as the dedicated
     * verification endpoint does — it is the same code, checked the same way —
     * so an account that signs in has a verified phone, and saying otherwise
     * would leave the flag contradicting what just happened.</p>
     *
     * <p>The caller is responsible for having verified the code already.</p>
     *
     * @param user account whose number was just proven
     */
    @Transactional
    public void markPhoneVerified(User user) {

        userVerificationService.updatePhoneStatus(user, VerificationStatus.VERIFIED);
        advanceOnboarding(user, OnboardingStep.PHONE_VERIFICATION, OnboardingStep.PAN_VERIFICATION);

        log.info("Phone verified for user {}", user.getId());
    }

    /**
     * Rejects an address the authenticated user may not verify.
     *
     * <p>An address already on the account has to match — there is no
     * email-change flow, so the only one a user may verify is the one already on
     * their record. Comparing case-insensitively matches the OTP module's own
     * treatment of addresses.</p>
     *
     * <p>An account with no address yet may claim one, mirroring the phone
     * rule: signing in by phone creates an account before it has an email, so
     * the email step has to be able to supply the first one. It may only claim
     * an address no other account holds, so nobody can verify their way into
     * somebody else's.</p>
     */
    private void assertEmailCanBeVerified(User user, String normalizedEmail) {

        String registered = user.getEmail();

        if (registered == null) {

            if (userService.isEmailClaimed(normalizedEmail)) {
                throw new ConflictException(
                        "This email address is already registered to another account");
            }

            return;
        }

        if (!normalizedEmail.equalsIgnoreCase(registered.trim())) {
            throw new ForbiddenException(
                    "This email address is not associated with your account");
        }
    }

    /**
     * Rejects a number the authenticated user may not verify.
     *
     * <p>A number already on the account has to match. A number the account
     * does not have yet may be claimed — that is the point of the phone step —
     * but only if no other user holds it, so nobody can verify their way into
     * somebody else's number.</p>
     */
    private void assertPhoneCanBeVerified(User user, String normalizedPhone) {

        String registered = user.getMobileNumber();

        if (registered == null) {

            if (userService.isMobileNumberClaimed(normalizedPhone)) {
                throw new ConflictException(
                        "This phone number is already registered to another account");
            }

            return;
        }

        if (!normalizedPhone.equals(IdentifierNormalizer.normalize(OtpType.SMS, registered))) {
            throw new ForbiddenException(
                    "This phone number is not associated with your account");
        }
    }

    /**
     * Moves the account on from the step that was just completed.
     *
     * <p>Only that exact step advances. A user who is already further along —
     * or who verified channels out of order — keeps the progress they have;
     * onboarding never moves backwards.</p>
     *
     * <p>The step landed on is the next one the account has not already
     * satisfied, which is what makes signing in by phone work. Such an account
     * arrives with its phone already proven and begins at the email step; once
     * the email is verified the next step would nominally be the phone, which
     * is done. Stopping there would strand the account on a step whose only
     * endpoint answers "already verified" and never moves on, so satisfied
     * steps are walked past instead.</p>
     */
    private void advanceOnboarding(User user, OnboardingStep completed, OnboardingStep next) {

        if (user.getOnboardingStep() != completed) {
            return;
        }

        userService.updateOnboardingStep(user, nextOutstanding(user, next));
    }

    /**
     * Walks forward from {@code candidate} to the first step this account has
     * not yet satisfied.
     */
    private OnboardingStep nextOutstanding(User user, OnboardingStep candidate) {

        OnboardingStep step = candidate;

        while (step != OnboardingStep.COMPLETED && isSatisfied(user, step)) {
            step = successor(step);
        }

        return step;
    }

    /**
     * @return {@code true} when the account has already completed {@code step}
     */
    private boolean isSatisfied(User user, OnboardingStep step) {

        UserVerification verification = userVerificationService.getVerification(user);

        return switch (step) {
            case EMAIL_VERIFICATION -> verification.getEmailStatus() == VerificationStatus.VERIFIED;
            case PHONE_VERIFICATION -> verification.getPhoneStatus() == VerificationStatus.VERIFIED;
            case PAN_VERIFICATION -> verification.getPanStatus() == VerificationStatus.VERIFIED;
            case BANK_ACCOUNT_VERIFICATION ->
                    verification.getBankAccountStatus() == VerificationStatus.VERIFIED;
            case COMPLETED -> true;
        };
    }

    private OnboardingStep successor(OnboardingStep step) {

        return switch (step) {
            case EMAIL_VERIFICATION -> OnboardingStep.PHONE_VERIFICATION;
            case PHONE_VERIFICATION -> OnboardingStep.PAN_VERIFICATION;
            case PAN_VERIFICATION -> OnboardingStep.BANK_ACCOUNT_VERIFICATION;
            case BANK_ACCOUNT_VERIFICATION, COMPLETED -> OnboardingStep.COMPLETED;
        };
    }
}
