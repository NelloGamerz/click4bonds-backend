package com.click4bonds.app.Modules.Party.Dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.click4bonds.app.Modules.Party.Enums.PartyType;
import com.click4bonds.app.Modules.Party.Model.Party;

/**
 * A party as an API caller sees it, with its financial records summarized.
 *
 * <p>PAN and bank account numbers in the nested responses are decrypted. The
 * entity is never serialized directly.</p>
 */
public record PartyResponse(
        UUID id,
        PartyType partyType,
        String legalName,
        String contactEmail,
        String mobileNumber,
        String address,
        PanDetailsResponse pan,
        List<BankAccountResponse> bankAccounts,
        List<DematAccountResponse> dematAccounts,
        Instant createdAt) {

    public static PartyResponse of(
            Party party,
            PanDetailsResponse pan,
            List<BankAccountResponse> bankAccounts,
            List<DematAccountResponse> dematAccounts) {

        return new PartyResponse(
                party.getId(),
                party.getPartyType(),
                party.getLegalName(),
                party.getContactEmail(),
                party.getMobileNumber(),
                party.getAddress(),
                pan,
                bankAccounts,
                dematAccounts,
                party.getCreatedAt());
    }
}
