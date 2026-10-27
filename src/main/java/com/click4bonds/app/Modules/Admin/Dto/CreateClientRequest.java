package com.click4bonds.app.Modules.Admin.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The details an administrator records for a client.
 *
 * <p>Only the name is required. The contact fields are what the back office
 * happens to know at the time, and a client recorded without them is still a
 * client.</p>
 */
public record CreateClientRequest(

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
