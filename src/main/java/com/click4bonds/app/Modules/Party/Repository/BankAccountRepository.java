package com.click4bonds.app.Modules.Party.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Party.Model.BankAccount;
import com.click4bonds.app.Modules.Party.Model.Party;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByPartyOrderByCreatedAtAsc(Party party);

    List<BankAccount> findByPartyAndPrimaryAccountTrue(Party party);

    Optional<BankAccount> findByIdAndParty(UUID id, Party party);
}
