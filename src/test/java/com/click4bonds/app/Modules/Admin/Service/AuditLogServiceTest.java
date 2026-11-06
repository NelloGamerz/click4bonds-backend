package com.click4bonds.app.Modules.Admin.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.click4bonds.app.Modules.Admin.Dto.AuditLogResponse;
import com.click4bonds.app.Modules.Admin.Model.AuditLog;
import com.click4bonds.app.Modules.Admin.Repository.AuditLogRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

class AuditLogServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    private final UserService userService = mock(UserService.class);

    private final AuditLogService service =
            new AuditLogService(auditLogRepository, userService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsWhoActedWhatTheyDidAndWhen() {

        UUID actorId = UUID.randomUUID();
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();

        authenticate(actorId);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(userService.findUser(actorId)).thenReturn(Optional.of(actor));

        service.record(
                "UPDATE_USER",
                "USER",
                UUID.randomUUID(),
                "role EMPLOYEE -> ADMIN");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog entry = captor.getValue();

        assertEquals(actor, entry.getPerformedBy());
        assertEquals(UserRole.ADMIN, entry.getPerformedByRole());
        assertEquals("UPDATE_USER", entry.getAction());
        assertEquals("USER", entry.getEntityType());
        assertEquals("role EMPLOYEE -> ADMIN", entry.getDetails());

        // The interceptor must not write a second row for the same request.
        assertTrue(AuditLogService.currentRequestAlreadyRecorded());
    }

    @Test
    void ignoresACustomerAndLeavesTheRequestUnmarked() {

        UUID actorId = UUID.randomUUID();
        User customer = User.builder().id(actorId).role(UserRole.CUSTOMER).build();

        authenticate(actorId);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(userService.findUser(actorId)).thenReturn(Optional.of(customer));

        service.record("UPDATE_USER", "USER", actorId, "not staff");

        verify(auditLogRepository, never()).save(any());
        assertFalse(AuditLogService.currentRequestAlreadyRecorded());
    }

    @Test
    void defaultsToTheLastTwentyFourHours() {

        UUID actorId = UUID.randomUUID();

        when(auditLogRepository.findByPerformedBy_IdAndCreatedAtBetween(
                any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getLogs(actorId, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);

        verify(auditLogRepository).findByPerformedBy_IdAndCreatedAtBetween(
                eq(actorId), from.capture(), to.capture(), any());

        Duration window = Duration.between(from.getValue(), to.getValue());

        assertTrue(window.compareTo(Duration.ofHours(24)) >= 0);
        assertTrue(window.compareTo(Duration.ofHours(24).plusMinutes(1)) < 0);
    }

    @Test
    void usesTheSuppliedRange() {

        UUID actorId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");

        when(auditLogRepository.findByPerformedBy_IdAndCreatedAtBetween(
                any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getLogs(actorId, from, to, PageRequest.of(0, 20));

        verify(auditLogRepository).findByPerformedBy_IdAndCreatedAtBetween(
                eq(actorId), eq(from), eq(to), any());
    }

    @Test
    void refusesARangeThatEndsBeforeItStarts() {

        assertThrows(
                BadRequestException.class,
                () -> service.getLogs(
                        UUID.randomUUID(),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        PageRequest.of(0, 20)));

        verify(auditLogRepository, never())
                .findByPerformedBy_IdAndCreatedAtBetween(any(), any(), any(), any());
    }

    @Test
    void mapsTheActorIntoTheResponse() {

        UUID actorId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .role(UserRole.EMPLOYEE)
                .build();

        AuditLog entry = AuditLog.builder()
                .id(entryId)
                .performedBy(actor)
                .performedByRole(UserRole.EMPLOYEE)
                .action("UPDATE_USER")
                .entityType("USER")
                .details("role EMPLOYEE -> ADMIN")
                .build();

        when(auditLogRepository.findByPerformedBy_IdAndCreatedAtBetween(
                any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        AuditLogResponse response = service
                .getLogs(actorId, Instant.now().minusSeconds(60), Instant.now(), PageRequest.of(0, 20))
                .getContent()
                .get(0);

        assertEquals(entryId, response.id());
        assertEquals(actorId, response.performedById());
        assertEquals("Ada Lovelace", response.performedByName());
        assertEquals("ada@example.com", response.performedByEmail());
        assertEquals("UPDATE_USER", response.action());
    }

    private static void authenticate(UUID userId) {

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("role", "ADMIN")
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
