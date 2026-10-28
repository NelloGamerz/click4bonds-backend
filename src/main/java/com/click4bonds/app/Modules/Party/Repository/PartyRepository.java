package com.click4bonds.app.Modules.Party.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Party.Model.Party;

public interface PartyRepository extends JpaRepository<Party, UUID> {
}
