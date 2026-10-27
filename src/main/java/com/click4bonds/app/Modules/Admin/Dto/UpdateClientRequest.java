package com.click4bonds.app.Modules.Admin.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The client details an administrator may change.
 *
 * <p>Every field is optional. A null field is left unchanged, while a supplied
 * value is validated and applied. This follows the PATCH convention used by
 * the other admin update requests.</p>
 */
public record UpdateClientRequest(

        @NotBlank(message = "Client name is required")
        @Size(max = 255, message = "Client name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Contact person must not exceed 255 characters")
        String contactPerson,

        @Email(message = "Email must be a well-formed address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9][0-9\\s\\-().]{6,24}$",
                message = "Invalid phone number")
        String mobileNumber,

        @Size(max = 1000, message = "Address must not exceed 1000 characters")
        String address) {
}
