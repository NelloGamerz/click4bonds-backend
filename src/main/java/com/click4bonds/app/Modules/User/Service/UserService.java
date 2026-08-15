package com.click4bonds.app.Modules.User.Service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Dto.ClerkWebhookRequest.ClerkUserData;
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

    public void createUser(ClerkUserData data) {

        if (userExists(data.id())) {
            log.info("User already exists {}", data.id());
            return;
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
                .onboardingCompleted(false)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);

        log.info("Created user {}", data.id());
    }

    public void updateUser(ClerkUserData data) {

        User user = getUser(data.id());

        String email = data.email_addresses()
                .stream()
                .findFirst()
                .orElse(data.email_addresses().getFirst())
                .email_address();

        user.setEmail(email);
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
