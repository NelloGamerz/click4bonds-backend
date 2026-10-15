package com.click4bonds.app.Modules.User.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.User;

public interface CustomerProfileRepository
        extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByUser(User user);

    Optional<CustomerProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
