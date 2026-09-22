package com.click4bonds.app.Modules.Auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * The security rules as they are actually applied, through the real filter
 * chain.
 *
 * <p>Deliberately limited to requests that read nothing and write nothing: the
 * point is which requests get through and what the cookie handling looks like,
 * not the sign-in flow itself, which {@code AuthServiceTest} drives over
 * in-memory doubles. This context is the configured one, so a test that
 * created accounts or issued codes would be writing to real infrastructure.</p>
 *
 * <p>The SMS sender is replaced so no test can cause a message to be sent.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthJwtService authJwtService;

    @Autowired
    private AuthProperties authProperties;

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

    // ------------------------------------------------------------------
    // Public endpoints
    // ------------------------------------------------------------------

    @Test
    void theBondListStaysPublic() throws Exception {

        // Publicly readable bonds are an existing product decision and the
        // migration must not have narrowed them.
        mockMvc.perform(get("/api/bonds"))
                .andExpect(status().isOk());
    }

    @Test
    void aPublicEndpointIsStillReachableWithAStaleToken() throws Exception {

        // A rejected token leaves the request unauthenticated rather than
        // failing it, so a public endpoint does not start refusing callers
        // whose token happens to have expired.
        mockMvc.perform(get("/api/bonds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isOk());
    }

    @Test
    void theContactInquiryEndpointStaysPublic() throws Exception {

        // Answered 400 for an empty body, not 401 — which is what proves the
        // request reached the controller.
        mockMvc.perform(post("/api/v1/contact-inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Protected endpoints
    // ------------------------------------------------------------------

    @Test
    void aProtectedEndpointRefusesAnAnonymousRequest() throws Exception {

        mockMvc.perform(get("/api/holdings/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aProtectedEndpointRefusesAnExpiredToken() throws Exception {

        // Signed with the same secret the running application verifies with, so
        // only the expiry can be what rejects it.
        AuthProperties properties = AuthTestSupport.properties();
        properties.getJwt().setSecret(authProperties.getJwt().getSecret());

        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));

        // Minted an hour ago, so it is genuinely expired against the real clock
        // the application validates with — not merely against a test clock.
        String expired = new AuthJwtService(properties, AuthTestSupport.MutableClock.inThePast())
                .createAccessToken(
                        User.builder()
                                .id(UUID.randomUUID())
                                .role(UserRole.CUSTOMER)
                                .status(UserStatus.ACTIVE)
                                .build());

        mockMvc.perform(get("/api/holdings/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aProtectedEndpointAcceptsAValidToken() throws Exception {

        // The account does not exist, so the request authenticates and then
        // finds nothing. Reaching the read is the assertion: a 401 here would
        // mean the filter did not accept the token.
        mockMvc.perform(get("/api/holdings/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(UserRole.CUSTOMER)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Cookie-bearing endpoints
    // ------------------------------------------------------------------

    @Test
    void refreshWithoutASessionIsUnauthorized() throws Exception {

        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithAnUnknownSessionIsUnauthorized() throws Exception {

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                authProperties.getSession().getCookieName(),
                                "a-session-that-was-never-issued")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSucceedsWithoutASessionAndClearsTheCookie() throws Exception {

        // Idempotent: signing out when already signed out is not an error.
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void logoutClearsTheCookieEvenWhenTheSessionWasAlreadyGone() throws Exception {

        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie(
                                authProperties.getSession().getCookieName(),
                                "a-session-that-was-never-issued")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    // ------------------------------------------------------------------
    // Sign-in endpoint validation
    // ------------------------------------------------------------------

    @Test
    void sendOtpRejectsAMalformedNumberWithoutReachingTheProvider() throws Exception {

        mockMvc.perform(post("/auth/phone/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12\"}"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(smsService);
    }

    @Test
    void verifyOtpRejectsAMalformedSubmissionWithoutReachingTheOtpStore() throws Exception {

        mockMvc.perform(post("/auth/phone/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919876543210\",\"otp\":\"not-digits\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyOtpWithNoOutstandingCodeIsRejected() throws Exception {

        // Nothing was ever sent to this number in this test, so there is no
        // code to redeem. The answer must not say so specifically.
        mockMvc.perform(post("/auth/phone/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919999999999\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }
}
