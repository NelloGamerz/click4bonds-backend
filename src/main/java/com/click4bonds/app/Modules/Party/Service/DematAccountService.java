package com.click4bonds.app.Modules.Party.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Party.Dto.DematAccountCreateRequest;
import com.click4bonds.app.Modules.Party.Dto.DematAccountResponse;
import com.click4bonds.app.Modules.Party.Dto.DematAccountUpdateRequest;
import com.click4bonds.app.Modules.Party.Model.DematAccount;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.DematAccountRepository;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Demat accounts belonging to a party.
 *
 * <p>A party may hold several. Every operation is scoped to the party, so an
 * identifier belonging to another party is treated as absent.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DematAccountService {

    private final DematAccountRepository dematAccountRepository;

    public DematAccountResponse add(Party party, DematAccountCreateRequest request) {

        return add(party, request, VerificationStatus.PENDING);
    }

    /**
     * Records a demat account entered by the back office as already verified —
     * what the staff member has checked against the depository statement is the
     * verification.
     */
    public DematAccountResponse addVerified(Party party, DematAccountCreateRequest request) {

        return add(party, request, VerificationStatus.VERIFIED);
    }

    private DematAccountResponse add(
            Party party,
            DematAccountCreateRequest request,
            VerificationStatus status) {

        DematAccount account = DematAccount.builder()
                .party(party)
                .depository(request.depository())
                .dpId(request.dpId().trim())
                .clientId(request.clientId().trim())
                .accountHolderName(request.accountHolderName().trim())
                .status(status)
                .build();

        account = dematAccountRepository.save(account);

        log.info("Demat account {} added for party {} as {}",
                account.getId(), party.getId(), status);

        return DematAccountResponse.of(account);
    }

    public List<DematAccountResponse> list(Party party) {

        return dematAccountRepository.findByPartyOrderByCreatedAtAsc(party)
                .stream()
                .map(DematAccountResponse::of)
                .toList();
    }

    public DematAccountResponse update(
            Party party, UUID accountId, DematAccountUpdateRequest request) {

        DematAccount account = require(party, accountId);

        if (request.depository() != null) {
            account.setDepository(request.depository());
        }
        if (request.dpId() != null) {
            account.setDpId(request.dpId().trim());
        }
        if (request.clientId() != null) {
            account.setClientId(request.clientId().trim());
        }
        if (request.accountHolderName() != null) {
            account.setAccountHolderName(request.accountHolderName().trim());
        }

        account = dematAccountRepository.save(account);

        log.info("Demat account {} updated for party {}", account.getId(), party.getId());

        return DematAccountResponse.of(account);
    }

    public void delete(Party party, UUID accountId) {

        dematAccountRepository.delete(require(party, accountId));

        log.info("Demat account {} removed from party {}", accountId, party.getId());
    }

    /** Records the outcome of an authorized demat account verification. */
    public DematAccountResponse updateStatus(
            Party party, UUID accountId, VerificationStatus status) {

        if (status == VerificationStatus.NOT_STARTED) {
            throw new BadRequestException("A verification outcome cannot be NOT_STARTED");
        }

        DematAccount account = require(party, accountId);
        account.setStatus(status);
        account = dematAccountRepository.save(account);

        log.info("Demat account {} verification for party {} set to {}",
                account.getId(), party.getId(), status);

        return DematAccountResponse.of(account);
    }

    private DematAccount require(Party party, UUID accountId) {

        return dematAccountRepository.findByIdAndParty(accountId, party)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demat account not found: " + accountId));
    }
}
