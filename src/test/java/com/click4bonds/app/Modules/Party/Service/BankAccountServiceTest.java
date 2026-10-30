package com.click4bonds.app.Modules.Party.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Party.Dto.BankAccountCreateRequest;
import com.click4bonds.app.Modules.Party.Dto.BankAccountResponse;
import com.click4bonds.app.Modules.Party.Dto.BankAccountUpdateRequest;
import com.click4bonds.app.Modules.Party.Model.BankAccount;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.BankAccountRepository;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

class BankAccountServiceTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("click4bonds-test-party-key-32by!".getBytes(StandardCharsets.UTF_8));

    private final BankAccountRepository repository = mock(BankAccountRepository.class);

    private final BankAccountService service =
            new BankAccountService(repository, new SecretCipher(KEY));

    private final Party party = Party.builder().id(UUID.randomUUID()).legalName("Test").build();

    private BankAccountServiceTest() {

        when(repository.save(any(BankAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static BankAccountCreateRequest request(boolean primary) {

        return new BankAccountCreateRequest(
                "Test Holder", "Test Bank", "Marine Lines", "1234567890", "HDFC0001234", primary);
    }

    @Test
    void aPartyMayHoldSeveralAccounts() {

        service.add(party, request(false));
        BankAccountResponse second = service.add(party, request(false));

        assertFalse(second.primaryAccount());
        assertEquals("1234567890", second.accountNumber());
    }

    @Test
    void makingAnAccountPrimaryClearsThePreviousOne() {

        BankAccount existing = BankAccount.builder()
                .id(UUID.randomUUID())
                .party(party)
                .primaryAccount(true)
                .build();

        when(repository.findByPartyAndPrimaryAccountTrue(party))
                .thenReturn(new ArrayList<>(List.of(existing)));

        BankAccountResponse response = service.add(party, request(true));

        assertFalse(existing.getPrimaryAccount());
        assertTrue(response.primaryAccount());
    }

    @Test
    void anotherPartysAccountIsReportedAsNotFound() {

        UUID accountId = UUID.randomUUID();
        when(repository.findByIdAndParty(accountId, party)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(party, accountId,
                        new BankAccountUpdateRequest(null, null, null, null, null, null)));
    }

    @Test
    void aSubmittedAccountIsNotVerified() {

        BankAccountResponse response = service.add(party, request(false));

        assertEquals(VerificationStatus.PENDING, response.status());
    }

    @Test
    void staffEntryIsVerifiedImmediately() {

        BankAccountResponse response = service.addVerified(party, request(false));

        assertEquals(VerificationStatus.VERIFIED, response.status());
    }

    @Test
    void aVerificationOutcomeCannotBeNotStarted() {

        assertThrows(BadRequestException.class,
                () -> service.updateStatus(party, UUID.randomUUID(), VerificationStatus.NOT_STARTED));
    }
}
