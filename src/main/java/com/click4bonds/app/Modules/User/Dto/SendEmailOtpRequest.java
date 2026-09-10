package com.click4bonds.app.Modules.User.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for an email verification code.
 *
 * <p>The address is supplied by the client rather than read from the token so
 * the server can confirm it is the one the account actually owns. The
 * ownership check lives in the verification service, not here.</p>
 */
public record SendEmailOtpRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 320, message = "Email must not exceed 320 characters")
        String email) {
}
