package com.click4bonds.app.Service;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Dto.ClerkWebhookRequest;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookService {

    private final UserService userService;

    public void handleWebhook(ClerkWebhookRequest request) {

        switch (request.type()) {

            case "user.created" -> userService.createUser(request.data());

            case "user.updated" -> userService.updateUser(request.data());

            case "user.deleted" -> userService.softDeleteUser(request.data().id());

            default -> log.info("Ignoring Clerk event {}", request.type());
        }
    }
}
