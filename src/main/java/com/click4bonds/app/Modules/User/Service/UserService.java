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
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
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
     * Resolves the account that signs in with an email address.
     *
     * <p>An address is put on an account only once its ownership has been
     * proven, so finding one here means the account also has a verified email.
     * A lookup that finds nothing is therefore an answer rather than something
     * to repair, exactly as it is for a number.</p>
     *
     * <p>The address is expected in canonical form — the same shape the OTP
     * module normalises submissions to — so the lookup and the stored value
     * agree regardless of the case the caller typed.</p>
     *
     * @param email canonical email address
     * @return the account that signs in with it
     * @throws ResourceNotFoundException when no account does
     */
    public User getUserByEmail(String email) {

        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException(SIGNUP_REQUIRED));
    }

    /**
     * Persists a newly described account, with the profile its role implies.
     *
     * <p>This is the single point where the profile invariant is established:
     * a {@code CUSTOMER} gets a {@link CustomerProfile} and nothing else, an
     * {@code ADMIN} or {@code EMPLOYEE} gets an {@link AdminProfile} and nothing
     * else. Enforcing it here rather than at each caller means the rule cannot
     * be forgotten by a new sign-up path, an import or a test fixture — there is
     * one way to create an account and it decides this for every account.</p>
     *
     * <p>Both sides of the association are set, and that is not belt and
     * braces: the profile owns the foreign key, so setting only
     * {@code user.setCustomerProfile(profile)} would insert a profile with a
     * null {@code user_id} and fail against the not-null constraint. A caller
     * that supplies its own profile — sign-up does, because the form collects
     * the answers — has it adopted rather than replaced, so nothing the caller
     * built is discarded.</p>
     *
     * @param user account to persist, not yet saved
     * @return the saved account
     * @throws IllegalStateException when the account carries the profile for the
     *         other kind of account
     */
    public User createUser(User user) {

        UserRole role = user.getRole();

        if (role == UserRole.CUSTOMER) {

            if (user.getAdminProfile() != null) {
                throw new IllegalStateException(
                        "A CUSTOMER may not have an AdminProfile");
            }

            CustomerProfile profile = user.getCustomerProfile() != null
                    ? user.getCustomerProfile()
                    : CustomerProfile.builder().build();

            profile.setUser(user);
            user.setCustomerProfile(profile);

        } else {

            if (user.getCustomerProfile() != null) {
                throw new IllegalStateException(
                        "A " + role + " may not have a CustomerProfile");
            }

            AdminProfile profile = user.getAdminProfile() != null
                    ? user.getAdminProfile()
                    : AdminProfile.builder().build();

            profile.setUser(user);
            user.setAdminProfile(profile);
        }

        User saved = userRepository.save(user);

        // Only a customer has channels to verify. A staff account is not asked
        // to prove an email, a phone, a PAN, a bank account or a demat account
        // through this flow, so no record is created for one.
        if (role == UserRole.CUSTOMER) {
            userVerificationService.createVerification(saved.getCustomerProfile());
        }

        // The identifier is logged; the number is personal data and is not.
        log.info("Created user {} with a {} profile", saved.getId(), role);

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
     * Looks up an account without treating its absence as an error.
     *
     * <p>Callers that ask whether an account exists — a permission check, for
     * one — decide for themselves what an absent account means, and for most of
     * them the answer is "refuse", not "throw". This is the sole way to reach
     * the account store from outside this class.</p>
     *
     * @param userId account identifier
     * @return the account, or an empty {@link Optional} when no such account exists
     */
    public Optional<User> findUser(UUID userId) {
        return userRepository.findById(userId);
    }

    /**
     * Pages through accounts for the admin listing.
     *
     * <p>A blank search term is not a filter that matches everything — it means
     * no search was asked for. It is normalised to null for the repository,
     * because the two are not the same thing to the query: null skips the
     * comparison entirely, while a blank string would be concatenated into
     * {@code LIKE '%%'} and match every row while looking like a real filter.</p>
     *
     * @param role     restrict to this role, or {@code null} for every role
     * @param search   term matched against name and address, or blank for none
     * @param pageable page request, carrying the ordering
     * @return one page of matching accounts
     */
    public Page<User> getUsers(UserRole role, String search, Pageable pageable) {

        return userRepository.findUsers(role, searchOrNull(search), pageable);
    }

    /**
     * @param search a caller's search term
     * @return the term, or null if it is absent or carries nothing to match on
     */
    private static String searchOrNull(String search) {

        return (search == null || search.isBlank()) ? null : search;
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
     * <p><strong>A change that crosses the customer/staff line is refused.</strong>
     * An account holds either a {@link CustomerProfile} or an
     * {@link AdminProfile}, never both, and the two carry data the other cannot
     * represent: making a customer into an employee would have to discard their
     * KYC state, the consents they gave and their entire per-channel
     * verification record; making an employee into a customer would have to
     * discard their module permissions. Neither is a role change — each is a
     * role change <em>plus</em> an irreversible deletion that the caller did not
     * ask for and cannot see from the request. Refusing is the only answer that
     * does not destroy data on the strength of a single parameter, and it makes
     * the move something an operator has to do deliberately.</p>
     *
     * <p>Moving between the two staff roles is allowed and keeps the same
     * profile, so an employee promoted to administrator keeps their employment
     * record and grants — and, being an {@code ADMIN}, stops being constrained
     * by them.</p>
     *
     * @param userId account identifier
     * @param role   role the account should hold
     * @return the updated account
     * @throws ResponseStatusException 404 when no such account exists
     * @throws ConflictException when the change would cross the customer/staff
     *         line and so need to delete the account's profile
     */
    public User updateRole(UUID userId, UserRole role) {

        User user = getUser(userId);

        if (user.getRole() == role) {
            return user;
        }

        boolean wasCustomer = user.getRole() == UserRole.CUSTOMER;
        boolean becomesCustomer = role == UserRole.CUSTOMER;

        if (wasCustomer != becomesCustomer) {

            String held = wasCustomer ? "CustomerProfile" : "AdminProfile";

            throw new ConflictException(
                    "Cannot change role from " + user.getRole() + " to " + role
                            + ": the account holds a " + held
                            + " and the other kind of account carries different data"
                            + " that would be lost. Create a new account instead.");
        }

        user.setRole(role);
        user = userRepository.save(user);

        log.info("Updated role for user {} to {}", user.getId(), role);

        return user;
    }

    /**
     * Moves the user to a different onboarding step.
     *
     * <p>The given user is expected to be a managed entity inside the caller's
     * transaction, so the change is flushed together with it.</p>
     *
     * <p>Onboarding is a customer lifecycle — every step it defines is a
     * verification channel — so the step is recorded on the customer profile.
     * The signature still takes the account because that is what the verification
     * flows hold, and they should not have to know where the step is kept.</p>
     *
     * @param user           user whose progress is being recorded
     * @param onboardingStep step the user has reached
     * @throws IllegalStateException when the account is not a customer
     */
    public void updateOnboardingStep(User user, OnboardingStep onboardingStep) {

        CustomerProfile profile = user.getCustomerProfile();

        if (profile == null) {
            throw new IllegalStateException(
                    "Onboarding is only tracked for customer accounts");
        }

        profile.setOnboardingStep(onboardingStep);

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
}
