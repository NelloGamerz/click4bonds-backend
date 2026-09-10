package com.click4bonds.app.Modules.User.Service;

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
     * @param clerkUserId authenticated user, as identified by the token subject
     * @param email       address to verify
     * @return a message that says nothing about the code itself
     */
    public VerificationResponse sendEmailOtp(String clerkUserId, String email) {

        User user = userService.getUserByClerkId(clerkUserId);
        String normalizedEmail = IdentifierNormalizer.normalize(OtpType.EMAIL, email);

        assertEmailBelongsTo(user, normalizedEmail);

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
     * @param clerkUserId authenticated user, as identified by the token subject
     * @param email       address being verified
     * @param otp         submitted code
     * @return a message that never echoes the code
     */
    @Transactional
    public VerificationResponse verifyEmailOtp(String clerkUserId, String email, String otp) {

        User user = userService.getUserByClerkId(clerkUserId);
        String normalizedEmail = IdentifierNormalizer.normalize(OtpType.EMAIL, email);

        assertEmailBelongsTo(user, normalizedEmail);

        // Throws when the code is wrong, unknown, expired, already redeemed or
        // out of attempts — so nothing below runs on anything but a real code.
        otpService.verifyOtp(OtpType.EMAIL, normalizedEmail, otp);

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
     * @param clerkUserId authenticated user, as identified by the token subject
     * @param phone       number to verify
     * @return a message that never echoes the code
     */
    public VerificationResponse sendPhoneOtp(String clerkUserId, String phone) {

        User user = userService.getUserByClerkId(clerkUserId);
        String normalizedPhone = IdentifierNormalizer.normalize(OtpType.SMS, phone);

        assertPhoneCanBeVerified(user, normalizedPhone);

        UserVerification verification = userVerificationService.getVerification(user);

        if (verification.getPhoneStatus() == VerificationStatus.VERIFIED) {
            return new VerificationResponse(PHONE_ALREADY_VERIFIED);
        }

        // Issuing a code proves nothing: the statuses are left untouched.
        otpService.generateOtp(OtpType.SMS, normalizedPhone);

        return new VerificationResponse(PHONE_OTP_GENERATED);
    }

    /**
     * Verifies a submitted phone code and records the result.
     *
     * <p>Ownership of the number is only accepted once the code has been
     * accepted: a number the account did not already own is bound to it here,
     * after proof, and never before.</p>
     *
     * @param clerkUserId authenticated user, as identified by the token subject
     * @param phone       number being verified
     * @param otp         submitted code
     * @return a message that never echoes the code
     */
    @Transactional
    public VerificationResponse verifyPhoneOtp(String clerkUserId, String phone, String otp) {

        User user = userService.getUserByClerkId(clerkUserId);
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
     * Rejects an address the authenticated user does not own.
     *
     * <p>Email is unique per account and there is no email-change flow, so the
     * only address a user may verify is the one already on their record.
     * Comparing case-insensitively matches the OTP module's own treatment of
     * addresses.</p>
     */
    private void assertEmailBelongsTo(User user, String normalizedEmail) {

        String registered = user.getEmail();

        if (registered == null || !normalizedEmail.equalsIgnoreCase(registered.trim())) {
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
     * onboarding never moves backwards, and a step is never skipped.</p>
     */
    private void advanceOnboarding(User user, OnboardingStep completed, OnboardingStep next) {

        if (user.getOnboardingStep() != completed) {
            return;
        }

        userService.updateOnboardingStep(user, next);
    }
}
