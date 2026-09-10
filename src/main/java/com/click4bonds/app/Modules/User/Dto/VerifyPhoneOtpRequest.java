package com.click4bonds.app.Modules.User.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Submission of a phone verification code.
 *
 * <p>The code's length is deliberately not constrained here: it is configured
 * through {@code otp.length} and enforced by the OTP module, so duplicating it
 * in a validation annotation would be one more place to keep in step.</p>
 */
public record VerifyPhoneOtpRequest(

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9][0-9\\s\\-().]{6,24}$",
                message = "Invalid phone number")
        String phone,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]+$", message = "OTP must contain digits only")
        String otp) {
}
