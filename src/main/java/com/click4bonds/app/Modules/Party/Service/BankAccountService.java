package com.click4bonds.app.Modules.Party.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Party.Dto.BankAccountCreateRequest;
import com.click4bonds.app.Modules.Party.Dto.BankAccountResponse;
import com.click4bonds.app.Modules.Party.Dto.BankAccountUpdateRequest;
import com.click4bonds.app.Modules.Party.Model.BankAccount;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.BankAccountRepository;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bank accounts belonging to a party, and the one-primary rule.
 *
 * <p>Every operation is scoped to a party: an account id from another party is
 * reported as not found, so a customer cannot reach a record by guessing an
 * identifier.</p>
 *
 * <p>When a primary account is set, the previously primary account is cleared
 * in the same transaction and the new one is saved after it. The partial unique
 * index {@code uk_bank_account_primary} is the concurrency backstop: two
 * transactions racing for primary both clear, and the second insert fails
 * against the index instead of leaving two primaries.</p>
 *
 * <p>Account numbers are encrypted at rest and masked in responses, and are
 * never logged.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final SecretCipher secretCipher;

    public BankAccountResponse add(Party party, BankAccountCreateRequest request) {

        return add(party, request, VerificationStatus.PENDING);
    }

    /**
     * Records an account entered by the back office as already verified — what
     * the staff member has checked against the bank statement or cancellation
     * cheque is the verification.
     */
    public BankAccountResponse addVerified(Party party, BankAccountCreateRequest request) {

        return add(party, request, VerificationStatus.VERIFIED);
    }

    private BankAccountResponse add(
            Party party,
            BankAccountCreateRequest request,
            VerificationStatus status) {

        boolean primary = Boolean.TRUE.equals(request.primaryAccount());

        if (primary) {
            clearPrimary(party);
        }

        BankAccount account = BankAccount.builder()
                .party(party)
                .accountHolderName(request.accountHolderName().trim())
                .bankName(request.bankName().trim())
                .accountNumber(secretCipher.encrypt(request.accountNumber()))
                .ifscCode(request.ifscCode().trim())
                .primaryAccount(primary)
                .status(status)
                .build();

        account = bankAccountRepository.save(account);

        log.info("Bank account {} added for party {} as {}",
                account.getId(), party.getId(), status);

        return BankAccountResponse.of(account, request.accountNumber());
    }

    public List<BankAccountResponse> list(Party party) {

        return bankAccountRepository.findByPartyOrderByCreatedAtAsc(party)
                .stream()
                .map(account -> BankAccountResponse.of(
                        account,
                        secretCipher.decrypt(account.getAccountNumber())))
                .toList();
    }

    public BankAccountResponse update(Party party, UUID accountId, BankAccountUpdateRequest request) {

        BankAccount account = require(party, accountId);

        if (request.accountHolderName() != null) {
            account.setAccountHolderName(request.accountHolderName().trim());
        }
        if (request.bankName() != null) {
            account.setBankName(request.bankName().trim());
        }
        if (request.accountNumber() != null) {
            account.setAccountNumber(secretCipher.encrypt(request.accountNumber()));
        }
        if (request.ifscCode() != null) {
            account.setIfscCode(request.ifscCode().trim());
        }

        if (Boolean.TRUE.equals(request.primaryAccount())) {
            clearPrimary(party);
            account.setPrimaryAccount(true);
        } else if (Boolean.FALSE.equals(request.primaryAccount())) {
            account.setPrimaryAccount(false);
        }

        account = bankAccountRepository.save(account);

        log.info("Bank account {} updated for party {}", account.getId(), party.getId());

        return BankAccountResponse.of(account, secretCipher.decrypt(account.getAccountNumber()));
    }

    public void delete(Party party, UUID accountId) {

        bankAccountRepository.delete(require(party, accountId));

        log.info("Bank account {} removed from party {}", accountId, party.getId());
    }

    /** Records the outcome of an authorized bank account verification. */
    public BankAccountResponse updateStatus(
            Party party, UUID accountId, VerificationStatus status) {

        if (status == VerificationStatus.NOT_STARTED) {
            throw new BadRequestException("A verification outcome cannot be NOT_STARTED");
        }

        BankAccount account = require(party, accountId);
        account.setStatus(status);
        account = bankAccountRepository.save(account);

        log.info("Bank account {} verification for party {} set to {}",
                account.getId(), party.getId(), status);

        return BankAccountResponse.of(account, secretCipher.decrypt(account.getAccountNumber()));
    }

    private void clearPrimary(Party party) {

        for (BankAccount existing : bankAccountRepository.findByPartyAndPrimaryAccountTrue(party)) {
            existing.setPrimaryAccount(false);
        }
    }

    private BankAccount require(Party party, UUID accountId) {

        return bankAccountRepository.findByIdAndParty(accountId, party)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank account not found: " + accountId));
    }
}
