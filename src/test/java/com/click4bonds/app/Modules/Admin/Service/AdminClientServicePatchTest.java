package com.click4bonds.app.Modules.Admin.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.click4bonds.app.Modules.Admin.Dto.ClientResponse;
import com.click4bonds.app.Modules.Admin.Dto.UpdateClientRequest;
import com.click4bonds.app.Modules.User.Model.Client;
import com.click4bonds.app.Modules.User.Repository.ClientRepository;

class AdminClientServicePatchTest {

    private final ClientRepository clientRepository = mock(ClientRepository.class);

    private final AdminClientService service = new AdminClientService(clientRepository);

    @Test
    void patchLeavesFieldsNotIncludedInRequestUnchanged() {

        UUID id = UUID.randomUUID();
        Client client = Client.builder()
                .id(id)
                .name("Original name")
                .contactPerson("Original contact")
                .email("original@example.com")
                .mobileNumber("+919876543210")
                .address("Original address")
                .build();

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientRepository.save(client)).thenReturn(client);

        service.patch(
                id,
                new UpdateClientRequest(
                        "Updated name",
                        null,
                        null,
                        null,
                        null));

        assertEquals("Updated name", client.getName());
        assertEquals("Original contact", client.getContactPerson());
        assertEquals("original@example.com", client.getEmail());
        assertEquals("+919876543210", client.getMobileNumber());
        assertEquals("Original address", client.getAddress());
        verify(clientRepository).save(client);
    }

    @Test
    void getAllReturnsClientsMappedToResponses() {

        Client client = Client.builder()
                .id(UUID.randomUUID())
                .name("Acme")
                .email("acme@example.com")
                .build();

        Sort sort = Sort.by(Sort.Direction.ASC, "name");
        when(clientRepository.findAll(sort))
                .thenReturn(List.of(client));

        List<ClientResponse> clients = service.getAll();

        assertEquals(1, clients.size());
        assertEquals(client.getId(), clients.get(0).id());
        assertEquals("Acme", clients.get(0).name());
        verify(clientRepository).findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
}
