package com.click4bonds.app.Modules.Admin.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.ClientResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientRequest;
import com.click4bonds.app.Modules.Admin.Dto.UpdateClientRequest;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.User.Model.Client;
import com.click4bonds.app.Modules.User.Repository.ClientRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminClientService {

    private final ClientRepository clientRepository;

    public ClientResponse create(CreateClientRequest request) {

        Client client = Client.builder()
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

        return ClientResponse.from(clientRepository.save(client));
    }

    public void delete(UUID id) {

        clientRepository.delete(require(id));
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
