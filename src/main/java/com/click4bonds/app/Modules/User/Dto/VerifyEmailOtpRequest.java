package com.click4bonds.app.Modules.User.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Submission of an email verification code.
 *
 * <p>The code's length is deliberately not constrained here: it is configured
 * through {@code otp.length} and enforced by the OTP module, so duplicating it
 * in a validation annotation would be one more place to keep in step.</p>
 */
public record VerifyEmailOtpRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 320, message = "Email must not exceed 320 characters")
        String email,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]+$", message = "OTP must contain digits only")
        String otp) {
}
