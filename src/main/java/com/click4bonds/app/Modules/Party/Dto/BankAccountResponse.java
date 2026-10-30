package com.click4bonds.app.Modules.Party.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.Party.Model.BankAccount;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

/** A bank account with its account number decrypted for the caller. */
public record BankAccountResponse(
        UUID id,
        String accountHolderName,
        String bankName,
        String branchLocation,
        String accountNumber,
        String ifscCode,
        Boolean primaryAccount,
        VerificationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * @param account               stored record, holding ciphertext
     * @param plainAccountNumber    decrypted number
     */
    public static BankAccountResponse of(BankAccount account, String plainAccountNumber) {

        return new BankAccountResponse(
                account.getId(),
                account.getAccountHolderName(),
                account.getBankName(),
                account.getBranchLocation(),
                plainAccountNumber,
                account.getIfscCode(),
                account.getPrimaryAccount(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
