package com.click4bonds.app.Modules.User.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.User.Model.Client;

public interface ClientRepository
        extends JpaRepository<Client, UUID> {

    /** Bounded because this feeds a picker, not a listing. */
    List<Client> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
