package com.click4bonds.app.Modules.Admin.Controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.ClientResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientRequest;
import com.click4bonds.app.Modules.Admin.Dto.UpdateClientRequest;
import com.click4bonds.app.Modules.Admin.Service.AdminClientService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Client administration.
 *
 * <p>Two gates, as on the other admin controllers: the role gate says the caller
 * is a member of staff, and the module gate says an {@code EMPLOYEE} has been
 * granted {@code CLIENT_MANAGEMENT}. An {@code ADMIN} passes the module gate for
 * everything. A caller without the grant is refused by Spring Security, which
 * surfaces as a 403 through {@code GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class AdminClientController {

    private final AdminClientService adminClientService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminClientService.create(request));
    }

    /**
     * Clients matching a name, for the picker on the deal-creation form.
     *
     * <p>Gated by {@code DEAL_CREATION}, not {@code CLIENT_MANAGEMENT}: the
     * employee looking a client up is doing it in order to create that client's
     * deal, and need not be able to administer the client records themselves.</p>
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<List<ClientResponse>> searchClients(@RequestParam String name) {

        return ResponseEntity.ok(adminClientService.search(name));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {

        return ResponseEntity.ok(adminClientService.patch(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {

        adminClientService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
