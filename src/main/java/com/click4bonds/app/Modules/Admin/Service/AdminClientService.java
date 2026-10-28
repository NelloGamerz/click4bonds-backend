package com.click4bonds.app.Modules.Admin.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.ClientResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientRequest;
import com.click4bonds.app.Modules.Admin.Dto.UpdateClientRequest;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Party.Enums.PartyType;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Service.PanDetailsService;
import com.click4bonds.app.Modules.Party.Service.PartyService;
import com.click4bonds.app.Modules.User.Model.Client;
import com.click4bonds.app.Modules.User.Repository.ClientRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminClientService {

    /**
     * Default identity type for a client recorded without one. A back-office
     * client is most often a legal entity, and the admin can correct the type;
     * this is an assumption, not a verified fact.
     */
    private static final PartyType DEFAULT_PARTY_TYPE = PartyType.COMPANY;

    private final ClientRepository clientRepository;
    private final PartyService partyService;
    private final PanDetailsService panDetailsService;

    public ClientResponse create(CreateClientRequest request) {

        Party party = partyService.create(
                request.partyType() != null ? request.partyType() : DEFAULT_PARTY_TYPE,
                request.name(),
                request.email(),
                request.mobileNumber(),
                request.address());

        Client client = Client.builder()
                .party(party)
                .name(request.name())
                .contactPerson(request.contactPerson())
                .email(request.email())
                .mobileNumber(request.mobileNumber())
                .address(request.address())
                .build();

        return ClientResponse.from(clientRepository.save(client));
    }

    /**
     * Applies the supplied changes to a client. Null fields are left unchanged.
     *
     * <p>The same identity changes are mirrored onto the client's party so the
     * two do not drift. Client-only fields — the contact person — have no party
     * counterpart and stay here.</p>
     */
    public ClientResponse patch(UUID id, UpdateClientRequest request) {

        Client client = require(id);

        if (request.name() != null) {
            client.setName(request.name());
        }
        if (request.contactPerson() != null) {
            client.setContactPerson(request.contactPerson());
        }
        if (request.email() != null) {
            client.setEmail(request.email());
        }
        if (request.mobileNumber() != null) {
            client.setMobileNumber(request.mobileNumber());
        }
        if (request.address() != null) {
            client.setAddress(request.address());
        }

        if (client.getParty() != null) {
            partyService.applyClientIdentity(
                    client.getParty(),
                    request.name(),
                    request.email(),
                    request.mobileNumber(),
                    request.address());
        }

        return ClientResponse.from(clientRepository.save(client));
    }

    /**
     * Attaches a client record to an existing party.
     *
     * <p>This is the only path by which a client can come to share a party with
     * a registered customer, and it refuses a party whose PAN has not been
     * verified — without that proof the link would be an assumption that two
     * records are the same person. It is reachable only from the admin surface
     * behind {@code CLIENT_MANAGEMENT}; registration never calls it.</p>
     *
     * @throws ConflictException when the party has no verified PAN
     */
    public ClientResponse linkParty(UUID clientId, UUID partyId) {

        Client client = require(clientId);
        Party party = partyService.require(partyId);

        if (!panDetailsService.isVerified(party)) {
            throw new ConflictException(
                    "A client may only be linked to a party whose PAN is verified");
        }

        client.setParty(party);

        return ClientResponse.from(clientRepository.save(client));
    }

    public void delete(UUID id) {

        clientRepository.delete(require(id));
    }

    public List<ClientResponse> getAll() {

        return clientRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(ClientResponse::from)
                .toList();
    }

    public List<ClientResponse> search(String name) {

        if (name == null || name.isBlank()) {
            return List.of();
        }

        return clientRepository
                .findTop20ByNameContainingIgnoreCaseOrderByNameAsc(name.trim())
                .stream()
                .map(ClientResponse::from)
                .toList();
    }

    private Client require(UUID id) {

        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
    }
}
