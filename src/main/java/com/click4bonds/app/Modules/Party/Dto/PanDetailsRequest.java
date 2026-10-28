package com.click4bonds.app.Modules.Party.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A PAN submission.
 *
 * <p>Format validation is a cheap rejection of malformed input, not evidence
 * that the PAN exists or belongs to the party — only the verification workflow
 * establishes that.</p>
 */
public record PanDetailsRequest(

        @NotBlank(message = "PAN number is required")
        @Pattern(
                regexp = "^[A-Za-z]{5}[0-9]{4}[A-Za-z]$",
                message = "PAN must be five letters, four digits and one letter")
        String panNumber) {
}
