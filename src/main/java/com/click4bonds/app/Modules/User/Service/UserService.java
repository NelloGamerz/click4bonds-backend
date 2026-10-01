package com.click4bonds.app.Modules.User.Service;

import java.util.Optional;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
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

    /**
     * The answer given to a caller whose number has no account behind it.
     *
     * <p>Shared by both sign-in endpoints so the same situation is worded the
     * same way whichever one runs into it.</p>
     */
    public static final String SIGNUP_REQUIRED = "User does not exist, please sign up";

    private final UserRepository userRepository;
    private final UserVerificationService userVerificationService;
    private final AnalyticsService analyticsService;

    /**
     * Resolves the account that signs in with a phone number.
     *
     * <p>Accounts are no longer conjured from a number on the strength of a
     * code: the number has to belong to an account that was signed up first, so
     * a lookup that finds nothing is an answer rather than something to repair.
     * The number is expected in canonical form — the same shape the OTP module
     * normalises submissions to — so the lookup and the stored value agree.</p>
     *
     * @param mobileNumber canonical phone number
     * @return the account that signs in with it
     * @throws ResourceNotFoundException when no account does
     */
    public User getUserByMobileNumber(String mobileNumber) {

        return userRepository.findByMobileNumber(mobileNumber).orElseThrow(() -> new ResourceNotFoundException(SIGNUP_REQUIRED));
    }

    /**
     * @param mobileNumber canonical phone number
     * @return {@code true} when an account already signs in with this number
     */
    public boolean isMobileNumberClaimed(String mobileNumber) {
        return userRepository.existsByMobileNumber(mobileNumber);
    }

    /**
     * Persists a newly described account.
     *
     * <p>Used by sign-up, which is the only flow that collects a profile before
     * ownership of the number has been proven. The caller supplies the entity,
     * which keeps this module free of the sign-up request type; what this
     * method adds is everything every new account needs regardless of where it
     * came from — the row itself, and the verification record that tracks each
     * channel separately.</p>
     *
     * @param user account to persist, not yet saved
     * @return the saved account
     */
    public User createUser(User user) {

        User saved = userRepository.save(user);
        userVerificationService.createVerification(saved);

        // The identifier is logged; the number is personal data and is not.
        log.info("Created user {} from a sign-up", saved.getId());

        return saved;
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
     * Pages through accounts for the admin listing.
     *
     * <p>A blank search term is not a filter that matches everything — it means
     * no search was asked for, and the unfiltered query is cheaper.</p>
     *
     * @param role     restrict to this role, or {@code null} for every role
     * @param search   term matched against name and address, or blank for none
     * @param pageable page request
     * @return one page of matching accounts
     */
    public Page<User> getUsers(UserRole role, String search, Pageable pageable) {

        if (search == null || search.isBlank()) {
            return userRepository.findUsers(role, pageable);
        }

        return userRepository.searchUsers(role, search, pageable);
    }

    /**
     * Moves the user to a different status.
     *
     * @param userId account identifier
     * @param status status the account should hold
     * @return the updated account
     * @throws ResponseStatusException 404 when no such account exists
     */
    public User updateStatus(UUID userId, UserStatus status) {

        User user = getUser(userId);
        user.setStatus(status);

        log.info("Updated status for user {} to {}", user.getId(), status);

        return userRepository.save(user);
    }

    /**
     * Changes a user's role.
     *
     * <p>The change is the whole operation. It used to also enqueue an outbox
     * event so the role could be pushed to the external identity provider that
     * held the authoritative copy; that provider is gone and this database is
     * now the only place a role lives, so there is no second system to notify.
     * Authorization reads the role from here — via the access token's
     * {@code role} claim — so the change takes effect at the user's next token
     * refresh.</p>
     *
     * @param userId account identifier
     * @param role   role the account should hold
     * @return the updated account
     * @throws ResponseStatusException 404 when no such account exists
     */
    public User updateRole(UUID userId, UserRole role) {

        User user = getUser(userId);

        if (user.getRole() != role) {
            user.setRole(role);
            user = userRepository.save(user);

            log.info("Updated role for user {} to {}", user.getId(), role);
        }

        return user;
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
        if (onboardingStep == OnboardingStep.COMPLETED) {
            analyticsService.track(
                    AnalyticsEventType.PROFILE_COMPLETE,
                    user.getId(),
                    null,
                    null,
                    "WEB",
                    "PROFILE",
                    java.util.Map.of());
        }
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
        analyticsService.track(
                AnalyticsEventType.PROFILE_UPDATE,
                user.getId(),
                null,
                null,
                "WEB",
                "PROFILE",
                java.util.Map.of("field", "mobileNumber"));
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
        analyticsService.track(
                AnalyticsEventType.PROFILE_UPDATE,
                user.getId(),
                null,
                null,
                "WEB",
                "PROFILE",
                java.util.Map.of("field", "email"));
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
        analyticsService.track(
                AnalyticsEventType.SIGNUP,
                saved.getId(),
                null,
                null,
                "WEB",
                "AUTHENTICATION",
                java.util.Map.of("method", "PHONE_OTP"));

        return saved;
    }

    /**
     * Checks whether the user with the given identifier has the requested role.
     *
     * @param userId user identifier
     * @param role   role to check
     * @return {@code true} when the user exists and has the requested role
     */
//    public boolean hasRole(UUID userId, UserRole role) {
//
//        if (userId == null || role == null) {
//            return false;
//        }
//
//        return userRepository.findRoleByUserId(userId).map(userRole -> userRole == role).orElse(false);
//    }
//    public boolean hasRole(UUID userId, UserRole role) {
//
//        if (userId == null || role == null) {
//            log.warn(
//                    "hasRole called with null value: userId={}, role={}",
//                    userId,
//                    role
//            );
//            return false;
//        }
//
//        Optional<UserRole> result = userRepository.findRoleByUserId(userId);
//
//        log.info(
//                "Role lookup: userId={}, requestedRole={}, databaseRole={}",
//                userId,
//                role,
//                result.orElse(null)
//        );
//
//        boolean matches = result
//                .map(userRole -> userRole == role)
//                .orElse(false);
//
//        log.info(
//                "Role result: userId={}, requestedRole={}, databaseRole={}, matches={}",
//                userId,
//                role,
//                result.orElse(null),
//                matches
//        );
//
//        return matches;
//    }
    public boolean hasRole(UUID userId, UserRole role) {

        if (userId == null || role == null) {
            log.warn("Role check skipped: userId={}, requestedRole={}", userId, role);
            return false;
        }

        log.info("Looking up user role: userId={}, requestedRole={}", userId, role);

        Optional<UserRole> roleResult = userRepository.findRoleByUserId(userId);

        log.info("Role lookup result: userId={}, requestedRole={}, databaseRole={}, present={}", userId, role, roleResult.orElse(null), roleResult.isPresent());

        boolean matches = roleResult.map(userRole -> userRole == role).orElse(false);

        log.info("Role check result: userId={}, requestedRole={}, databaseRole={}, matches={}", userId, role, roleResult.orElse(null), matches);

        return matches;
    }


}
