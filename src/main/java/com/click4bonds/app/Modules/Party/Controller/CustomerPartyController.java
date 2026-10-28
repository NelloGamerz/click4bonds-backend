package com.click4bonds.app.Modules.Party.Controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Service.BankAccountService;
import com.click4bonds.app.Modules.Party.Service.DematAccountService;
import com.click4bonds.app.Modules.Party.Service.PanDetailsService;
import com.click4bonds.app.Modules.Party.Service.PartyService;
import com.click4bonds.app.Modules.User.Service.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The signed-in customer's own party and its financial records.
 *
 * <p>The party is always resolved from the access token's subject. No endpoint
 * here accepts a party id, so there is nothing to tamper with in order to reach
 * somebody else's account.</p>
 *
 * <p>A customer may submit a PAN and manage bank and demat accounts. They may
 * not set any verification status: submission leaves records {@code PENDING}
 * for the authorized verification surface to decide.</p>
 */
@RestController
@RequestMapping("/api/users/party")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Party", description = "The signed-in customer's party and financial records")
public class CustomerPartyController {

    private final UserService userService;
    private final PartyService partyService;
    private final PanDetailsService panDetailsService;
    private final BankAccountService bankAccountService;
    private final DematAccountService dematAccountService;

    @GetMapping
    public ResponseEntity<PartyResponse> getParty(@AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(partyService.detail(party(jwt).getId()));
    }

    @PutMapping("/pan")
    public ResponseEntity<PanDetailsResponse> submitPan(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PanDetailsRequest request) {

        return ResponseEntity.ok(
                panDetailsService.submit(party(jwt), request.panNumber()));
    }

    @GetMapping("/bank-accounts")
    public ResponseEntity<List<BankAccountResponse>> listBankAccounts(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(bankAccountService.list(party(jwt)));
    }

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankAccountResponse> addBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BankAccountCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bankAccountService.add(party(jwt), request));
    }

    @PatchMapping("/bank-accounts/{accountId}")
    public ResponseEntity<BankAccountResponse> updateBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody BankAccountUpdateRequest request) {

        return ResponseEntity.ok(
                bankAccountService.update(party(jwt), accountId, request));
    }

    @DeleteMapping("/bank-accounts/{accountId}")
    public ResponseEntity<Void> deleteBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId) {

        bankAccountService.delete(party(jwt), accountId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/demat-accounts")
    public ResponseEntity<List<DematAccountResponse>> listDematAccounts(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(dematAccountService.list(party(jwt)));
    }

    @PostMapping("/demat-accounts")
    public ResponseEntity<DematAccountResponse> addDematAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DematAccountCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dematAccountService.add(party(jwt), request));
    }

    @PatchMapping("/demat-accounts/{accountId}")
    public ResponseEntity<DematAccountResponse> updateDematAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody DematAccountUpdateRequest request) {

        return ResponseEntity.ok(
                dematAccountService.update(party(jwt), accountId, request));
    }

    @DeleteMapping("/demat-accounts/{accountId}")
    public ResponseEntity<Void> deleteDematAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId) {

        dematAccountService.delete(party(jwt), accountId);

        return ResponseEntity.noContent().build();
    }

    private Party party(Jwt jwt) {

        return partyService.requireForUser(
                userService.getUserById(jwt.getSubject()).getId());
    }
}
