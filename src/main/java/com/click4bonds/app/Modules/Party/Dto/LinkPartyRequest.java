package com.click4bonds.app.Modules.Party.Dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * An authorized request to attach a client record to an existing party.
 *
 * <p>Party identifiers are never taken from registration data; linking is a
 * deliberate back-office operation.</p>
 */
public record LinkPartyRequest(

        @NotNull(message = "Party id is required")
        UUID partyId) {
}
