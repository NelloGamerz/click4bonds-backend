package com.click4bonds.app.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByClerkUserId(String clerkUserId);
    boolean existsByClerkUserId(String clerkUserId);
}
