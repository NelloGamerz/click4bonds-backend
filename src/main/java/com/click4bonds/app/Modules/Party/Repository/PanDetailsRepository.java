package com.click4bonds.app.Modules.Party.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Party.Model.PanDetails;
import com.click4bonds.app.Modules.Party.Model.Party;

public interface PanDetailsRepository extends JpaRepository<PanDetails, UUID> {

    Optional<PanDetails> findByParty(Party party);

    Optional<PanDetails> findByPartyId(UUID partyId);
}
