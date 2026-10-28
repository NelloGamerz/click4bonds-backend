package com.click4bonds.app.Modules.Party.Dto;

import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

import jakarta.validation.constraints.NotNull;

/** The result of an authorized verification workflow. */
public record VerificationUpdateRequest(

        @NotNull(message = "Verification status is required")
        VerificationStatus status) {
}
