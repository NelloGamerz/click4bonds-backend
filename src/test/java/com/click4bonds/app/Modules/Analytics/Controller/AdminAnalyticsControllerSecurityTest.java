package com.click4bonds.app.Modules.Analytics.Controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsSummary;
import com.click4bonds.app.Modules.Analytics.Service.AdminAnalyticsQueryService;
import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * Who may read another user's analytics, checked through the real filter chain.
 *
 * <p>This is the test that proves {@code @EnableMethodSecurity} is doing
 * something. Before it was added, every {@code @PreAuthorize} in this codebase
 * was inert and any signed-in user could reach {@code /api/admin/**}; these
 * cases assert the opposite, and they assert it against the running context
 * rather than against the annotation.</p>
 *
 * <p>The role comes from the token's claim, mapped to an authority by
 * {@code SecurityConfig.jwtAuthenticationConverter}, so these tests exercise the
 * whole path from "what the token says" to "what the endpoint allows".</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAnalyticsControllerSecurityTest {

    private static final UUID TARGET_USER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthJwtService authJwtService;

    @MockitoBean
    private AdminAnalyticsQueryService adminAnalyticsQueryService;

    /** Replaced so no test can cause a message to be sent. */
    @MockitoBean
    private com.click4bonds.app.Modules.Sms.service.SmsService smsService;

    private String tokenFor(UserRole role) {

        return authJwtService.createAccessToken(
                User.builder()
                        .id(UUID.randomUUID())
                        .role(role)
                        .status(UserStatus.ACTIVE)
                        .build());
    }

    private String adminAnalyticsPath() {
        return "/api/admin/analytics/users/" + TARGET_USER + "/events";
    }

    @Test
    void anAdminMayReadAnotherUsersAnalytics() throws Exception {

        when(adminAnalyticsQueryService.getUserEvents(
                any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new AnalyticsUserEventsResponse(
                        TARGET_USER,
                        List.of(),
                        0,
                        false,
                        null,
                        new AnalyticsUserEventsSummary(0L, Map.of())));

        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_USER.toString()));
    }

    @Test
    void aCustomerMayNotReadAnotherUsersAnalytics() throws Exception {

        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeMayNotReadAnotherUsersAnalytics() throws Exception {

        // EMPLOYEE is the interesting case: it is an internal role, but it is
        // not ADMIN, and the endpoint must not treat "staff" as "allowed".
        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.EMPLOYEE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerIsToldToAuthenticateRatherThanForbidden() throws Exception {

        // 401 rather than 403: there is no credential at all, so signing in is
        // the remedy, and the filter chain answers before any role is consulted.
        mockMvc.perform(get(adminAnalyticsPath()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRejectionIsForbiddenAndNotAServerError() throws Exception {

        // AuthorizationDeniedException is raised inside the handler, so it
        // passes back through Spring MVC — where GlobalExceptionHandler's
        // catch-all would otherwise claim it and answer 500. This asserts the
        // explicit handler is in place and that the body is the API's own shape.
        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void thePreExistingAdminEndpointsAreEnforcedToo() throws Exception {

        // The same switch that protects the new endpoint also turns on the
        // annotations that were previously decorative everywhere else. This is
        // the app-wide behaviour change, stated as a test rather than as a note.
        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMalformedTargetUserIdIsABadRequest() throws Exception {

        mockMvc.perform(get("/api/admin/analytics/users/not-a-uuid/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnrecognisedEventTypeIsABadRequest() throws Exception {

        mockMvc.perform(get(adminAnalyticsPath())
                .param("eventType", "NOT_A_REAL_EVENT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
    }
}
