package com.click4bonds.app.Modules.Party.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Party.Model.DematAccount;
import com.click4bonds.app.Modules.Party.Model.Party;

public interface DematAccountRepository extends JpaRepository<DematAccount, UUID> {

    List<DematAccount> findByPartyOrderByCreatedAtAsc(Party party);

    Optional<DematAccount> findByIdAndParty(UUID id, Party party);
}
