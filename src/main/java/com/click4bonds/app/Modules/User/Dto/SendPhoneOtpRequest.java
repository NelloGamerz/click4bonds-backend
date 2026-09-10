package com.click4bonds.app.Modules.User.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request for a phone verification code.
 *
 * <p>The pattern only rejects values that could not be a phone number at all;
 * the authoritative check is the OTP module's identifier normaliser, which
 * also decides the canonical form the code is stored under.</p>
 */
public record SendPhoneOtpRequest(

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9][0-9\\s\\-().]{6,24}$",
                message = "Invalid phone number")
        String phone) {
}
