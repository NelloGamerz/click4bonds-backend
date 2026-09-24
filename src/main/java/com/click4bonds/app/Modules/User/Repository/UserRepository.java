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
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);

    /**
     * Looks an account up by the number it signs in with.
     *
     * @param mobileNumber canonical form, as produced by the OTP module's
     *                     identifier normaliser
     */
    Optional<User> findByMobileNumber(String mobileNumber);

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
            WHERE (:role IS NULL OR u.role = :role)
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

    @Query("""
            SELECT u
            FROM User u
            WHERE (:role IS NULL OR u.role = :role)
            """)
    Page<User> findUsers(
            @Param("role") UserRole role,
            Pageable pageable);

    @Query("SELECT u.role FROM User u WHERE u.id = :userId")
    Optional<UserRole> findRoleByUserId(@Param("userId") UUID userId);
}
