package com.click4bonds.app.Modules.User.Service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Dto.ClerkWebhookRequest.ClerkUserData;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
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
     * Creates the local user record for a Clerk signup.
     *
     * Also creates the initial verification record for the user.
     * 
     * @param data user payload delivered by the Clerk webhook
     * @return the created user, or an empty optional when the user already
     *         existed (Clerk retries webhooks, and callers use this to avoid
     *         repeating side effects such as the welcome email)
     */
    public Optional<User> createUser(ClerkUserData data) {

        if (userExists(data.id())) {
            log.info("User already exists {}", data.id());
            return Optional.empty();
        }

        String email = data.email_addresses()
                .stream()
                .findFirst()
                .orElse(data.email_addresses().getFirst())
                .email_address();

        User user = User.builder()
                .clerkUserId(data.id())
                .email(email)
                .firstName(data.first_name())
                .lastName(data.last_name())
                .profileImage(data.image_url())
                // .onboardingCompleted(false)
                .onboardingStep(OnboardingStep.EMAIL_VERIFICATION)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        userVerificationService.createVerification(savedUser);

        log.info("Created user {}", data.id());

        return Optional.of(savedUser);
    }

    public void updateUser(ClerkUserData data) {

        User user = getUser(data.id());

        String email = data.email_addresses()
                .stream()
                .findFirst()
                .orElse(data.email_addresses().getFirst())
                .email_address();

        user.setEmail(email);
        user.setMobileNumber(null);
        user.setFirstName(data.first_name());
        user.setLastName(data.last_name());
        user.setProfileImage(data.image_url());

        log.info("Updated user {}", data.id());
    }

    public void softDeleteUser(String clerkUserId) {

        User user = getUser(clerkUserId);

        markDeleted(user);

        log.info("Soft deleted user {}", clerkUserId);
    }

    public User getUserByClerkId(String clerkUserId) {
        return getUser(clerkUserId);
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

        log.info(
                "Updated onboarding step for user {} to {}",
                user.getId(),
                onboardingStep);
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
     * @return {@code true} when a user already owns this mobile number
     */
    public boolean isMobileNumberClaimed(String mobileNumber) {
        return userRepository.existsByMobileNumber(mobileNumber);
    }

    protected User getUser(String clerkUserId) {
        return userRepository.findByClerkUserId(clerkUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    protected void markDeleted(User user) {

        user.setStatus(UserStatus.DELETED);
    }

    protected boolean userExists(String clerkUserId) {
        return userRepository.existsByClerkUserId(clerkUserId);
    }
}
