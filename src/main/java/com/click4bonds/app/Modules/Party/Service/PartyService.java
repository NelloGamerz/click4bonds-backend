package com.click4bonds.app.Modules.Party.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Party.Dto.PartyResponse;
import com.click4bonds.app.Modules.Party.Enums.PartyType;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.PartyRepository;
import com.click4bonds.app.Modules.User.Enums.UserType;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Party creation and resolution.
 *
 * <p>Creating a party never creates an account, and creating an account does not
 * make the party a login. The two are separate records that happen to be linked
 * by the customer profile or by a client record.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PartyService {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final PanDetailsService panDetailsService;
    private final BankAccountService bankAccountService;
    private final DematAccountService dematAccountService;

    public Party create(
            PartyType partyType,
            String legalName,
            String contactEmail,
            String mobileNumber,
            String address) {

        Party party = Party.builder()
                .partyType(partyType)
                .legalName(legalName)
                .contactEmail(contactEmail)
                .mobileNumber(mobileNumber)
                .address(address)
                .build();

        return partyRepository.save(party);
    }

    /**
     * Creates the party a newly registered customer's profile points at.
     *
     * <p>Seeded from what the account knows at registration — name, number and,
     * when present, email. The email is usually absent at sign-up because the
     * account begins with a phone number; the party keeps its business contact
     * information and is not silently updated every time the login identity
     * changes.</p>
     */
    public Party createForCustomer(User user, UserType userType) {

        String legalName = Stream.of(user.getFirstName(), user.getLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(" "));

        if (legalName.isEmpty()) {
            legalName = "Customer " + user.getId();
        }

        return create(
                PartyType.fromUserType(userType),
                legalName,
                user.getEmail(),
                user.getMobileNumber(),
                null);
    }

    public Party require(UUID partyId) {

        return partyRepository.findById(partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found: " + partyId));
    }

    /**
     * Resolves the party the authenticated customer may act on.
     *
     * <p>The account is loaded here, inside this service's transaction, rather
     * than handed in from a caller's completed one: the profile and its party
     * are lazy associations, and touching them on a detached account would
     * fail. The party is taken from the customer's own profile, never from a
     * request parameter, which is what keeps one customer out of another's
     * financial records.</p>
     */
    public Party requireForUser(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getCustomerProfile() == null) {
            throw new ForbiddenException("Parties are only available to customer accounts");
        }

        Party party = user.getCustomerProfile().getParty();

        if (party == null) {
            throw new ResourceNotFoundException("No party is linked to this account");
        }

        return party;
    }

    /** Mirrors the client's own identity fields onto its party. */
    public void applyClientIdentity(
            Party party,
            String legalName,
            String contactEmail,
            String mobileNumber,
            String address) {

        if (legalName != null) {
            party.setLegalName(legalName);
        }
        if (contactEmail != null) {
            party.setContactEmail(contactEmail);
        }
        if (mobileNumber != null) {
            party.setMobileNumber(mobileNumber);
        }
        if (address != null) {
            party.setAddress(address);
        }
    }

    public PartyResponse detail(UUID partyId) {

        return detail(require(partyId));
    }

    public PartyResponse detail(Party party) {

        return PartyResponse.of(
                party,
                panDetailsService.find(party).orElse(null),
                bankAccountService.list(party),
                dematAccountService.list(party));
    }
}
