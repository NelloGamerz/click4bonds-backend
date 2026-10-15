package com.click4bonds.app.Modules.Analytics.Controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventSearchResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsSummary;
import com.click4bonds.app.Modules.Analytics.Service.AdminAnalyticsQueryService;
import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.AdminProfileRepository;
import com.click4bonds.app.Modules.User.Repository.UserRepository;

/**
 * Who may read another user's analytics, checked through the real filter chain.
 *
 * <p>This is the test that proves {@code @EnableMethodSecurity} is doing
 * something. Before it was added, every {@code @PreAuthorize} in this codebase
 * was inert and any signed-in user could reach {@code /api/admin/**}; these
 * cases assert the opposite, and they assert it against the running context
 * rather than against the annotation.</p>
 *
 * <p>An admin endpoint now answers two questions rather than one: whether the
 * caller is a member of staff — the role claim, mapped to an authority by
 * {@code SecurityConfig.jwtAuthenticationConverter} — and whether that member of
 * staff may use this particular module. The second is answered from the
 * database on every request by {@code AdminPermissionService}, not from the
 * token, so that a grant withdrawn since the token was minted stops working
 * immediately. The two repositories that check reads are therefore stubbed here,
 * which is also what lets a test state an account's role and grants directly
 * instead of needing seeded rows.</p>
 *
 * <p>What has <em>not</em> changed: a token naming an account that no longer
 * exists is refused. It used to be enough for a token to be well-formed and
 * carry the right role; now the account behind it has to be there too.</p>
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

    /** Backs the permission check, so an account's role can be stated directly. */
    @MockitoBean
    private UserRepository userRepository;

    /** Backs the permission check, so a staff account's grants can be stated directly. */
    @MockitoBean
    private AdminProfileRepository adminProfileRepository;

    /** Replaced so no test can cause a message to be sent. */
    @MockitoBean
    private com.click4bonds.app.Modules.Sms.service.SmsService smsService;

    private String tokenFor(UserRole role) {
        return tokenFor(role, Set.of());
    }

    /**
     * Mints a token for a member of staff holding the given grants, and makes the
     * account findable.
     *
     * <p>The account has to be in the repository as well as in the token,
     * because the permission check re-reads the role and the grants from the
     * database. A token alone is not enough to reach an admin endpoint any more
     * — see {@link #aTokenForAnAccountThatNoLongerExistsIsRefused()}.</p>
     *
     * @param role    role the account holds
     * @param modules modules its admin profile grants
     */
    private String tokenFor(UserRole role, Set<AdminModule> modules) {

        UUID id = UUID.randomUUID();

        User user = User.builder()
                .id(id)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        if (modules != null && !modules.isEmpty()) {

            AdminProfile profile = AdminProfile.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .accessConfig(new LinkedHashSet<>(modules))
                    .build();

            when(adminProfileRepository.findByUserId(id)).thenReturn(Optional.of(profile));
        }

        return authJwtService.createAccessToken(user);
    }

    private String adminAnalyticsPath() {
        return "/api/admin/analytics/users/" + TARGET_USER + "/events";
    }

    private void stubAnalytics() {

        when(adminAnalyticsQueryService.getUserEvents(
                any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new AnalyticsUserEventsResponse(
                        TARGET_USER,
                        List.of(),
                        0,
                        false,
                        null,
                        new AnalyticsUserEventsSummary(0L, Map.of())));
    }

    @Test
    void anAdminMayReadAnotherUsersAnalytics() throws Exception {

        stubAnalytics();

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

        // EMPLOYEE is the interesting case: it is an internal role, so it passes
        // the staff gate, but it has been granted no modules and so is refused by
        // the permission check. "Staff" is not "allowed".
        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.EMPLOYEE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeGrantedTheModuleMayReadAnotherUsersAnalytics() throws Exception {

        // The counterpart, and the point of the whole mechanism: the same role,
        // the same endpoint, and a different answer because of a grant.
        stubAnalytics();

        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenFor(UserRole.EMPLOYEE, Set.of(AdminModule.ANALYTICS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_USER.toString()));
    }

    @Test
    void aGrantDoesNotWidenAccessToOtherModules() throws Exception {

        // Granted analytics, and nothing else: reaching the analytics endpoint
        // must not carry the account into the user-administration one.
        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenFor(UserRole.EMPLOYEE, Set.of(AdminModule.ANALYTICS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenForAnAccountThatNoLongerExistsIsRefused() throws Exception {

        // A well-formed token with an ADMIN claim, for an account with no row.
        // The role is read from the account, not the token, so there is nothing
        // to read and the answer is no.
        String orphanToken = authJwtService.createAccessToken(
                User.builder()
                        .id(UUID.randomUUID())
                        .role(UserRole.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .build());

        mockMvc.perform(get(adminAnalyticsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orphanToken))
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
    void anAdminMaySearchEveryUsersAnalytics() throws Exception {

        when(adminAnalyticsQueryService.searchEvents(any(), any(), any(), any(), anyInt()))
                .thenReturn(new AnalyticsEventSearchResponse(
                        List.of(),
                        0,
                        false,
                        null));

        mockMvc.perform(get(searchPath())
                .param("eventType", "BOND_VIEW")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void aCustomerMayNotSearchEveryUsersAnalytics() throws Exception {

        // The search reaches further than the per-user endpoint — it is not
        // narrowed by a path variable — so the role gate has to hold here too.
        mockMvc.perform(get(searchPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeMayNotSearchEveryUsersAnalytics() throws Exception {

        mockMvc.perform(get(searchPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.EMPLOYEE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousSearchCallerIsToldToAuthenticate() throws Exception {

        mockMvc.perform(get(searchPath()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnrecognisedEventTypeIsRejectedByTheSearchToo() throws Exception {

        mockMvc.perform(get(searchPath())
                .param("eventType", "NOT_A_REAL_EVENT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
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
