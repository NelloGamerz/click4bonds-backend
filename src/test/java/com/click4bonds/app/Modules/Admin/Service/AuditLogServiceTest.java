package com.click4bonds.app.Modules.Admin.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.click4bonds.app.Modules.Admin.Model.AuditLog;
import com.click4bonds.app.Modules.Admin.Repository.AuditLogRepository;
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
