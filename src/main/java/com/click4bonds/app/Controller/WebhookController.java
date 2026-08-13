package com.click4bonds.app.Controller;


import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.click4bonds.app.Dto.ClerkWebhookRequest;
import com.click4bonds.app.Service.WebhookService;
import com.svix.Webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    @Value("${clerk.webhook.secret}")
    private String webhookSecret;

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/clerk")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("svix-id") String svixId,
            @RequestHeader("svix-timestamp") String timestamp,
            @RequestHeader("svix-signature") String signature) {

        try {

            HttpHeaders headers = HttpHeaders.of(
                    Map.of(
                            "svix-id", List.of(svixId),
                            "svix-timestamp", List.of(timestamp),
                            "svix-signature", List.of(signature)),
                    (k, v) -> true);

            new Webhook(webhookSecret).verify(payload, headers);

            ClerkWebhookRequest request = objectMapper.readValue(payload, ClerkWebhookRequest.class);

            webhookService.handleWebhook(request);

            return ResponseEntity.ok().build();

        } catch (Exception ex) {

            log.error("Failed to process Clerk webhook", ex);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
