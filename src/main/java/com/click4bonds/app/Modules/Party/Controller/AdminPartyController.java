package com.click4bonds.app.Modules.Party.Controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Party.Dto.BankAccountCreateRequest;
import com.click4bonds.app.Modules.Party.Dto.BankAccountResponse;
import com.click4bonds.app.Modules.Party.Dto.BankAccountUpdateRequest;
import com.click4bonds.app.Modules.Party.Dto.DematAccountCreateRequest;
import com.click4bonds.app.Modules.Party.Dto.DematAccountResponse;
import com.click4bonds.app.Modules.Party.Dto.DematAccountUpdateRequest;
import com.click4bonds.app.Modules.Party.Dto.PanDetailsRequest;
import com.click4bonds.app.Modules.Party.Dto.PanDetailsResponse;
import com.click4bonds.app.Modules.Party.Dto.PartyResponse;
import com.click4bonds.app.Modules.Party.Dto.VerificationUpdateRequest;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Service.BankAccountService;
import com.click4bonds.app.Modules.Party.Service.DematAccountService;
import com.click4bonds.app.Modules.Party.Service.PanDetailsService;
import com.click4bonds.app.Modules.Party.Service.PartyService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Back-office access to parties and the authorized verification surface.
 *
 * <p>Gated by {@code CLIENT_MANAGEMENT}, the same grant that covers client
 * records. Responses include PAN and bank account numbers in the clear.</p>
 *
 * <p>Details entered here by a staff member are recorded as already
 * {@code VERIFIED}: the entry is the record of what the staff member checked.
 * Customer submissions still arrive {@code PENDING} and are decided through the
 * verification endpoints.</p>
 */
@RestController
@RequestMapping("/api/admin/parties")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Admin parties", description = "Party records and verification for back-office staff")
public class AdminPartyController {

    private final PartyService partyService;
    private final PanDetailsService panDetailsService;
    private final BankAccountService bankAccountService;
    private final DematAccountService dematAccountService;

    @GetMapping("/{partyId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<PartyResponse> getParty(@PathVariable UUID partyId) {

        return ResponseEntity.ok(partyService.detail(partyId));
    }

    /** Records the party's PAN as verified, because staff entered it. */
    @PutMapping("/{partyId}/pan")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<PanDetailsResponse> recordPan(
            @PathVariable UUID partyId,
            @Valid @RequestBody PanDetailsRequest request) {

        return ResponseEntity.ok(
                panDetailsService.submitVerified(partyService.require(partyId), request.panNumber()));
    }

    /** Records a bank account as verified, because staff entered it. */
    @PostMapping("/{partyId}/bank-accounts")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<BankAccountResponse> recordBankAccount(
            @PathVariable UUID partyId,
            @Valid @RequestBody BankAccountCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bankAccountService.addVerified(partyService.require(partyId), request));
    }

    /** Edits a bank account. The existing verification status is left as it was. */
    @PatchMapping("/{partyId}/bank-accounts/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<BankAccountResponse> updateBankAccount(
            @PathVariable UUID partyId,
            @PathVariable UUID accountId,
            @Valid @RequestBody BankAccountUpdateRequest request) {

        return ResponseEntity.ok(
                bankAccountService.update(partyService.require(partyId), accountId, request));
    }

    /** Records a demat account as verified, because staff entered it. */
    @PostMapping("/{partyId}/demat-accounts")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<DematAccountResponse> recordDematAccount(
            @PathVariable UUID partyId,
            @Valid @RequestBody DematAccountCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dematAccountService.addVerified(partyService.require(partyId), request));
    }

    /** Edits a demat account. The existing verification status is left as it was. */
    @PatchMapping("/{partyId}/demat-accounts/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<DematAccountResponse> updateDematAccount(
            @PathVariable UUID partyId,
            @PathVariable UUID accountId,
            @Valid @RequestBody DematAccountUpdateRequest request) {

        return ResponseEntity.ok(
                dematAccountService.update(partyService.require(partyId), accountId, request));
    }

    @PatchMapping("/{partyId}/pan/verification")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<PanDetailsResponse> updatePanVerification(
            @PathVariable UUID partyId,
            @Valid @RequestBody VerificationUpdateRequest request) {

        return ResponseEntity.ok(
                panDetailsService.updateStatus(partyService.require(partyId), request.status()));
    }

    @PatchMapping("/{partyId}/bank-accounts/{accountId}/verification")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<BankAccountResponse> updateBankVerification(
            @PathVariable UUID partyId,
            @PathVariable UUID accountId,
            @Valid @RequestBody VerificationUpdateRequest request) {

        Party party = partyService.require(partyId);

        return ResponseEntity.ok(
                bankAccountService.updateStatus(party, accountId, request.status()));
    }

    @PatchMapping("/{partyId}/demat-accounts/{accountId}/verification")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CLIENT_MANAGEMENT')")
    public ResponseEntity<DematAccountResponse> updateDematVerification(
            @PathVariable UUID partyId,
            @PathVariable UUID accountId,
            @Valid @RequestBody VerificationUpdateRequest request) {

        Party party = partyService.require(partyId);

        return ResponseEntity.ok(
                dematAccountService.updateStatus(party, accountId, request.status()));
    }
}
