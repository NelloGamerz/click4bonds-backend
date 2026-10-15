package com.click4bonds.app.Modules.Admin.Dto;

import java.util.Set;

import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The details an administrator gives to open a staff account.
 *
 * <p>An address rather than a phone number, because that is how a member of
 * staff signs in: the account is opened by naming an address, and the person
 * confirms it by receiving a code at it. Nobody has proven the address belongs
 * to them until they do, which is why the account opens with its email
 * unverified.</p>
 *
 * <p>The name rules match {@code SignupRequest}'s exactly, because they are the
 * same claim about the same kind of value and two spellings of "a name" would
 * eventually disagree about a name.</p>
 *
 * <p>The employment details and the grants are optional and part of the same
 * request, so an account can be opened with everything it needs in one call
 * rather than being created bare and corrected twice afterwards.</p>
 */
public record CreateStaffUserRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a well-formed address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z .'-]*$",
                message = "First name may contain letters, spaces, apostrophes, hyphens and periods only")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z .'-]*$",
                message = "Last name may contain letters, spaces, apostrophes, hyphens and periods only")
        String lastName,

        /**
         * The internal role the account opens with.
         *
         * <p>Required and checked in the service rather than by an annotation:
         * the enum admits {@code CUSTOMER}, which would be a valid value for the
         * type and an invalid one for this endpoint, and a customer signing in
         * here would get a customer-token account with no customer record behind
         * it. The service refuses it with a message that says so.</p>
         */
        @NotNull(message = "Role is required")
        UserRole role,

        @Size(max = 255, message = "Employee code must not exceed 255 characters")
        String employeeCode,

        @Size(max = 255, message = "Department must not exceed 255 characters")
        String department,

        @Size(max = 255, message = "Designation must not exceed 255 characters")
        String designation,

        /**
         * The modules the account may use, or null for none.
         *
         * <p>Constrains an {@code EMPLOYEE}; an {@code ADMIN} is not limited by
         * its grants. Null and an empty set mean the same thing here — the
         * account is granted nothing — so there is no need to distinguish them
         * on a create.</p>
         */
        Set<AdminModule> accessConfig) {
}
