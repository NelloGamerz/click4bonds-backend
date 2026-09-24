package com.click4bonds.app.Modules.User.Service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserVerificationService userVerificationService;

    /**
     * Finds or creates the account behind a phone number.
     *
     * <p>Called only once ownership of the number has been proven, because that
     * proof is what the account's identity rests on. The number is expected in
     * canonical form — the same shape the OTP module normalises submissions to
     * — so the lookup and the stored value agree.</p>
     *
     * <p>A new account starts as an active customer at the beginning of
     * onboarding, with no email address: signing in establishes the phone, and
     * the email step is what comes next. Its phone verification is recorded as
     * complete, because it is.</p>
     *
     * @param mobileNumber canonical phone number that was just verified
     * @return the existing account, or the one created for this number
     */
    public User findOrCreateByMobileNumber(String mobileNumber) {

        return userRepository.findByMobileNumber(mobileNumber).orElseGet(() -> createPhoneUser(mobileNumber));
    }

    /**
     * @param mobileNumber canonical phone number
     * @return {@code true} when an account already signs in with this number
     */
    public boolean isMobileNumberClaimed(String mobileNumber) {
        return userRepository.existsByMobileNumber(mobileNumber);
    }

    /**
     * Resolves the account a token subject names.
     *
     * <p>The subject of an access token is {@code User.id}, and the
     * authentication filter has already rejected any token whose subject is not
     * a well-formed identifier — so a malformed value here means the caller was
     * handed something other than an authenticated subject.</p>
     *
     * @param userId subject of the authenticated token
     * @return the account it names
     * @throws ResponseStatusException 404 when no such account exists
     */
    public User getUserById(String userId) {

        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        try {
            return getUser(UUID.fromString(userId));

        } catch (IllegalArgumentException ex) {
            // Reported as unauthenticated rather than as a bad request: a
            // subject that is not an identifier means the caller's token is not
            // one this application issued.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
    }

    /**
     * @param userId account identifier
     * @return the account it names
     * @throws ResponseStatusException 404 when no such account exists
     */
    public User getUser(UUID userId) {

        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * Moves the user to a different onboarding step.
     *
     * <p>The given user is expected to be a managed entity inside the caller's
     * transaction, so the change is flushed together with it.</p>
     *
     * @param user           user whose progress is being recorded
     * @param onboardingStep step the user has reached
     */
    public void updateOnboardingStep(User user, OnboardingStep onboardingStep) {

        user.setOnboardingStep(onboardingStep);

        log.info("Updated onboarding step for user {} to {}", user.getId(), onboardingStep);
    }

    /**
     * Records the mobile number whose ownership the user has proven.
     *
     * <p>The value is expected to be already canonical — the same form the OTP
     * module normalises submissions to — so later submissions can be compared
     * against it directly.</p>
     *
     * @param user         user the number belongs to
     * @param mobileNumber canonical number
     */
    public void updateMobileNumber(User user, String mobileNumber) {

        user.setMobileNumber(mobileNumber);

        // The number itself is personal data and is never written to the log.
        log.info("Updated mobile number for user {}", user.getId());
    }

    /**
     * Records the address whose ownership the user has proven.
     *
     * <p>Assigned during the email verification step, which may be the first
     * time the account has an address at all.</p>
     *
     * @param user  user the address belongs to
     * @param email canonical address
     */
    public void updateEmail(User user, String email) {

        user.setEmail(email);

        log.info("Updated email for user {}", user.getId());
    }

    /**
     * @return {@code true} when another account already owns this address
     */
    public boolean isEmailClaimed(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Creates the account a phone number signs in with.
     *
     * <p>No welcome email is sent here, and that is not an oversight: there is
     * no address to send it to yet. The account has one only once the email
     * verification step completes.</p>
     */
    private User createPhoneUser(String mobileNumber) {

        User user = User.builder().mobileNumber(mobileNumber).onboardingStep(OnboardingStep.EMAIL_VERIFICATION).role(UserRole.CUSTOMER).status(UserStatus.ACTIVE).build();

        User saved = userRepository.save(user);
        userVerificationService.createVerification(saved);

        // The identifier is logged; the number is personal data and is not.
        log.info("Created user {} from a verified phone number", saved.getId());

        return saved;
    }

    /**
     * Checks whether the user with the given identifier has the requested role.
     *
     * @param userId user identifier
     * @param role   role to check
     * @return {@code true} when the user exists and has the requested role
     */
    public boolean hasRole(UUID userId, UserRole role) {

        if (userId == null || role == null) {
            return false;
        }

        return userRepository.findRoleByUserId(userId).map(userRole -> userRole == role).orElse(false);
    }
}
