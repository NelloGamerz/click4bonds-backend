package com.click4bonds.app.Modules.User.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.User;

public interface AdminProfileRepository
        extends JpaRepository<AdminProfile, UUID> {

    Optional<AdminProfile> findByUser(User user);

    Optional<AdminProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
