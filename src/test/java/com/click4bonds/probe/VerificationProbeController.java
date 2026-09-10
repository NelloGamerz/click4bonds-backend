package com.click4bonds.probe;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;

/**
 * Endpoints that do nothing but raise the failures the verification flow can
 * produce, so the exception advice can be driven over real HTTP.
 *
 * <p>Lives outside {@code com.click4bonds.app} on purpose: {@code @SpringBootTest}
 * component-scans that package, and a bean with mappings would otherwise be
 * pulled into every application context a test starts.</p>
 */
@RestController
public class VerificationProbeController {

    @GetMapping("/verification-probe/invalid-otp")
    public void invalidOtp() {
        throw new InvalidOtpException();
    }

    @GetMapping("/verification-probe/cooldown")
    public void cooldown() {
        throw new OtpResendCooldownException();
    }

    @GetMapping("/verification-probe/max-attempts")
    public void maxAttempts() {
        throw new OtpMaxAttemptsExceededException();
    }

    @GetMapping("/verification-probe/missing-user")
    public void missingUser() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    @GetMapping("/verification-probe/boom")
    public void boom() {
        throw new IllegalStateException("internal detail that must not escape");
    }
}
