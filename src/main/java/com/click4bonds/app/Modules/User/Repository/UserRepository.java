package com.click4bonds.app.Modules.User.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByClerkUserId(String clerkUserId);

    boolean existsByClerkUserId(String clerkUserId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByRoleAndStatus(
            UserRole role,
            UserStatus status,
            Pageable pageable);

    Page<User> findByRoleAndEmailContainingIgnoreCase(
            UserRole role,
            String email,
            Pageable pageable);

    long countByRole(UserRole role);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    @Query("""
                SELECT u
                FROM User u
                WHERE u.role = :role
                AND (
                    LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            """)
    Page<User> searchUsers(
            @Param("role") UserRole role,
            @Param("search") String search,
            Pageable pageable);
}
