package com.click4bonds.app.Modules.Party.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.Party.Enums.Depository;
import com.click4bonds.app.Modules.Party.Model.DematAccount;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

/** A demat account as an API caller sees it. */
public record DematAccountResponse(
        UUID id,
        Depository depository,
        String dpId,
        String clientId,
        String accountHolderName,
        VerificationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static DematAccountResponse of(DematAccount account) {

        return new DematAccountResponse(
                account.getId(),
                account.getDepository(),
                account.getDpId(),
                account.getClientId(),
                account.getAccountHolderName(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
