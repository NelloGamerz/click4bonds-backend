package com.click4bonds.app.Modules.Party.Dto;

import com.click4bonds.app.Modules.Party.Enums.Depository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A demat account submission.
 *
 * <p>Only presence, length and an alphanumeric character set are checked. The
 * depository's own rules differ by account type and by whether the account is
 * held jointly or by a company, so a stricter universal pattern here would
 * reject valid accounts.</p>
 */
public record DematAccountCreateRequest(

        @NotNull(message = "Depository is required")
        Depository depository,

        @NotBlank(message = "DP ID is required")
        @Size(max = 20, message = "DP ID must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "DP ID must be alphanumeric")
        String dpId,

        @NotBlank(message = "Client ID is required")
        @Size(max = 20, message = "Client ID must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Client ID must be alphanumeric")
        String clientId,

        @NotBlank(message = "Account holder name is required")
        @Size(max = 255, message = "Account holder name must not exceed 255 characters")
        String accountHolderName) {
}
