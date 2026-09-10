package com.click4bonds.app.Modules.User.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

public interface UserVerificationRepository
        extends JpaRepository<UserVerification, UUID> {

    Optional<UserVerification> findByUser(User user);

    Optional<UserVerification> findByUserId(UUID userId);

    boolean existsByUser(User user);
}
