package com.click4bonds.app.Modules.Common.Service;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Dto.UserRoleChangedEvent;
import com.click4bonds.app.Modules.Common.Enums.OutboxStatus;
import com.click4bonds.app.Modules.Common.Model.OutboxEvent;
import com.click4bonds.app.Modules.Common.Repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClerkOutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final ClerkService clerkService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void process() {

        List<OutboxEvent> events = outboxEventRepository
                .findTop100ByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING);

        for (OutboxEvent event : events) {

            try {

                event.setStatus(OutboxStatus.PROCESSING);
                outboxEventRepository.save(event);

                processEvent(event);

                event.setStatus(OutboxStatus.COMPLETED);
                event.setProcessedAt(Instant.now());
                event.setLastError(null);

            } catch (Exception e) {

                log.error(
                        "Failed to process outbox event {}",
                        event.getId(),
                        e);

                event.setRetryCount(
                        event.getRetryCount() + 1);

                event.setStatus(OutboxStatus.PENDING);
                event.setLastError(
                        e.getMessage());
            }

            outboxEventRepository.save(event);
        }
    }

    private void processEvent(
            OutboxEvent event) throws Exception {

        switch (event.getEventType()) {

            case "USER_ROLE_CHANGED" -> {

                UserRoleChangedEvent payload = objectMapper.readValue(
                        event.getPayload(),
                        UserRoleChangedEvent.class);

                clerkService.updateUserRole(
                        payload.clerkUserId(),
                        payload.role());
            }

            default -> throw new IllegalArgumentException(
                    "Unknown event type: "
                            + event.getEventType());
        }
    }
}
