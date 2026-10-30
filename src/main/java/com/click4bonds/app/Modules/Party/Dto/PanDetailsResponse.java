package com.click4bonds.app.Modules.Party.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.Party.Model.PanDetails;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

/** A PAN record with its number decrypted for the caller. */
public record PanDetailsResponse(
        UUID id,
        String panNumber,
        VerificationStatus status,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * @param details     stored record, holding ciphertext
     * @param plainPan    decrypted PAN
     */
    public static PanDetailsResponse of(PanDetails details, String plainPan) {

        return new PanDetailsResponse(
                details.getId(),
                plainPan,
                details.getStatus(),
                details.getVerifiedAt(),
                details.getCreatedAt(),
                details.getUpdatedAt());
    }
}
