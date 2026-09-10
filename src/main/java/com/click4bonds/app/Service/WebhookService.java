package com.click4bonds.app.Service;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Dto.ClerkWebhookRequest;
import com.click4bonds.app.Modules.Email.Service.EmailService;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookService {

    private final UserService userService;
    private final EmailService emailService;

    public void handleWebhook(ClerkWebhookRequest request) {

        switch (request.type()) {

            case "user.created" -> createUser(request);

            case "user.updated" -> userService.updateUser(request.data());

            case "user.deleted" -> userService.softDeleteUser(request.data().id());

            default -> log.info("Ignoring Clerk event {}", request.type());
        }
    }

    private void createUser(ClerkWebhookRequest request) {

        userService.createUser(request.data())
                .ifPresent(this::sendWelcomeEmail);
    }

    /**
     * Sends the welcome email for a freshly created account.
     *
     * <p>Best effort: a provider outage must not fail the Clerk webhook and
     * roll back the user that was just created, so delivery problems are
     * logged and swallowed.</p>
     */
    private void sendWelcomeEmail(User user) {

        try {

            emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());

        } catch (Exception ex) {

            log.warn(
                    "Welcome email could not be sent to user {}: {}",
                    user.getId(),
                    ex.getMessage());
        }
    }
}
