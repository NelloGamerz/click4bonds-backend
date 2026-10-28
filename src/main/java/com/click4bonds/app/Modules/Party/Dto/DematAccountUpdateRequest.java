package com.click4bonds.app.Modules.Party.Dto;

import com.click4bonds.app.Modules.Party.Enums.Depository;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A partial update of a demat account. Null fields are left unchanged. */
public record DematAccountUpdateRequest(

        Depository depository,

        @Size(max = 20, message = "DP ID must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "DP ID must be alphanumeric")
        String dpId,

        @Size(max = 20, message = "Client ID must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Client ID must be alphanumeric")
        String clientId,

        @Size(max = 255, message = "Account holder name must not exceed 255 characters")
        String accountHolderName) {
}
