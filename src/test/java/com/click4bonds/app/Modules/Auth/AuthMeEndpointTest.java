package com.click4bonds.app.Modules.Auth;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Auth.Dto.AuthResponse;
import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.Auth.Service.AuthService;
import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Dto.UserVerificationResponse;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

/**
 * The JSON these endpoints actually produce, over the real filter chain.
 *
 * <p>Serialization is the thing under test, not the service — the service is
 * mocked so the shapes can be driven exactly, and so that asserting on a
 * response never means writing to a database. The security rules are the real
 * ones, which is what makes the 401 cases meaningful: {@code /auth/**} is
 * public, and {@code /auth/me} has to sit outside that.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthMeEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthJwtService authJwtService;

    @Autowired
    private AuthProperties authProperties;

    @MockitoBean
    private AuthService authService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private String validToken() {

        return authJwtService.createAccessToken(
                com.click4bonds.app.Modules.User.Model.User.builder()
                        .id(userId)
                        .role(UserRole.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build());
    }

    private UserResponse profileWith(Boolean kycCompleted, UserVerificationResponse verification) {

        return UserResponse.builder()
                .id(userId)
                // Deliberately null: a phone-first account has no address, and
                // the key must still appear in the JSON.
                .email(null)
                .mobileNumber("+919876543210")
                .firstName("Asha")
                .lastName("Menon")
                .profileImage("https://example.com/a.png")
                .onboardingStep(kycCompleted ? OnboardingStep.COMPLETED : OnboardingStep.PAN_VERIFICATION)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-09-22T06:30:00Z"))
                .updatedAt(Instant.parse("2026-09-22T06:30:00Z"))
                .isKycCompleted(kycCompleted)
                .verification(verification)
                .build();
    }

    private UserVerificationResponse outstandingVerification() {

        return UserVerificationResponse.builder()
                .id(UUID.randomUUID())
                .emailStatus(VerificationStatus.VERIFIED)
                .phoneStatus(VerificationStatus.VERIFIED)
                .panStatus(VerificationStatus.NOT_STARTED)
                .bankAccountStatus(VerificationStatus.NOT_STARTED)
                .createdAt(Instant.parse("2026-09-22T06:30:00Z"))
                .updatedAt(Instant.parse("2026-09-22T06:30:00Z"))
                .build();
    }

    // ------------------------------------------------------------------
    // Trimmed sign-in and refresh responses
    // ------------------------------------------------------------------

    @Test
    void verifyOtpReturnsOnlyTheTokenAndTheUserId() throws Exception {

        given(authService.verifyPhoneOtp(any(), any(), any()))
                .willReturn(new AuthService.IssuedSession(
                        "session-id",
                        new AuthResponse("a.jwt.token", "Bearer", 900, userId)));

        mockMvc.perform(post("/auth/phone/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919876543210\",\"otp\":\"483920\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                // The profile and its nested verification used to ride along.
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.verification").doesNotExist());

        // The cookie is still set, and still not in the body.
        mockMvc.perform(post("/auth/phone/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919876543210\",\"otp\":\"483920\"}"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refreshReturnsOnlyTheTokenAndTheUserId() throws Exception {

        given(authService.refresh(any(), any()))
                .willReturn(new AuthService.IssuedSession(
                        "rotated-session-id",
                        new AuthResponse("a.fresh.jwt", "Bearer", 900, userId)));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                authProperties.getSession().getCookieName(), "old-session-id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a.fresh.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.verification").doesNotExist())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    // ------------------------------------------------------------------
    // The profile
    // ------------------------------------------------------------------

    @Test
    void meReturnsTheFullProfileWithVerificationWhileKycIsOutstanding() throws Exception {

        given(authService.getProfile(any()))
                .willReturn(profileWith(false, outstandingVerification()));

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.mobileNumber").value("+919876543210"))
                .andExpect(jsonPath("$.firstName").value("Asha"))
                .andExpect(jsonPath("$.lastName").value("Menon"))
                .andExpect(jsonPath("$.profileImage").value("https://example.com/a.png"))
                .andExpect(jsonPath("$.onboardingStep").value("PAN_VERIFICATION"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isKycCompleted").value(false))
                // A null email must still be a key, so a client can tell "no
                // address yet" from "this API does not return addresses". This
                // is what would break if NON_NULL were set on the class rather
                // than on the verification field.
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.verification").exists())
                .andExpect(jsonPath("$.verification.phoneStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verification.panStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.verification.bankAccountStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.verification.id").exists())
                .andExpect(jsonPath("$.verification.createdAt").exists())
                .andExpect(jsonPath("$.verification.updatedAt").exists());
    }

    @Test
    void meOmitsVerificationOnceKycIsComplete() throws Exception {

        given(authService.getProfile(any()))
                .willReturn(profileWith(true, null));

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isKycCompleted").value(true))
                .andExpect(jsonPath("$.onboardingStep").value("COMPLETED"))
                // Not null — absent. The key must not be present at all.
                .andExpect(jsonPath("$.verification").doesNotExist());

        // Every other field is still there.
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.mobileNumber").value("+919876543210"));
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    @Test
    void meRequiresAToken() throws Exception {

        // /auth/** is public, but /auth/me is declared ahead of that rule.
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void meRejectsAnInvalidToken() throws Exception {

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void meRejectsAnExpiredToken() throws Exception {

        AuthProperties expired = AuthTestSupport.properties();
        expired.getJwt().setSecret(authProperties.getJwt().getSecret());
        expired.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));

        String token = new AuthJwtService(expired, AuthTestSupport.MutableClock.inThePast())
                .createAccessToken(com.click4bonds.app.Modules.User.Model.User.builder()
                        .id(userId)
                        .role(UserRole.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsANonBearerScheme() throws Exception {

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theOtherAuthEndpointsStayPublic() throws Exception {

        // The /auth/me rule must not have narrowed the endpoints that are how a
        // caller obtains a token in the first place. Answered 400 for an empty
        // body, not 401 — which is what proves the request reached the
        // controller.
        mockMvc.perform(post("/auth/phone/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
