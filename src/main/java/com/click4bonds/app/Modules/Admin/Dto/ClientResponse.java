package com.click4bonds.app.Modules.Admin.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Model.Client;

public record ClientResponse(
        UUID id,
        UUID partyId,
        String name,
        String contactPerson,
        String email,
        String mobileNumber,
        String address,
        Instant createdAt) {

    public static ClientResponse from(Client client) {

        return new ClientResponse(
                client.getId(),
                client.getParty() != null ? client.getParty().getId() : null,
                client.getName(),
                client.getContactPerson(),
                client.getEmail(),
                client.getMobileNumber(),
                client.getAddress(),
                client.getCreatedAt());
    }
}
