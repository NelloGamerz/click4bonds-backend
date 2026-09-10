package com.click4bonds.app.Modules.Common.Exceptions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.click4bonds.probe.VerificationProbeController;

/**
 * The HTTP shape of every failure the verification endpoints can produce.
 *
 * <p>Driven against {@link VerificationProbeController} rather than a real
 * endpoint so the test exercises the advice itself — the same resolvers run
 * either way, including the ordering that makes these explicit handlers
 * necessary.</p>
 */
class GlobalExceptionHandlerOtpTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders
                .standaloneSetup(new VerificationProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReportAWrongOrExpiredOtpAsABadRequest() throws Exception {

        mockMvc.perform(get("/verification-probe/invalid-otp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"))
                .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
    }

    @Test
    void shouldReportAResendTooSoonAsTooManyRequests() throws Exception {

        mockMvc.perform(get("/verification-probe/cooldown"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));
    }

    @Test
    void shouldReportExhaustedAttemptsAsTooManyRequests() throws Exception {

        mockMvc.perform(get("/verification-probe/max-attempts"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_MAX_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void shouldHonourAnExplicitlyRaisedStatusInsteadOfReportingFiveHundred() throws Exception {

        mockMvc.perform(get("/verification-probe/missing-user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void shouldNotLeakInternalsInTheFallbackResponse() throws Exception {

        MvcResult result = mockMvc.perform(get("/verification-probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertFalse(body.contains("internal detail that must not escape"),
                "The response must not carry the internal message");
        assertFalse(body.contains("java.lang.IllegalStateException"),
                "The response must not carry an exception type");
    }
}
