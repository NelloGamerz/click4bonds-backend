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

    /**
     * Pages through accounts, optionally narrowed by role and by a search term.
     *
     * <p>One method rather than the two it replaces. The role and the search term
     * are independently optional, and both were expressed as a separate derived
     * query apiece — so the same filter had to be written, kept in step, and
     * separately tuned in more than one place. A null in either position means
     * "do not filter on this", which is what lets a caller pass null down
     * instead of choosing a method.</p>
     *
     * <p>The parentheses around the search disjunction are load-bearing: AND
     * binds tighter than OR, so without them the role filter would apply to the
     * first search branch alone and every account would match the rest.</p>
     *
     * <p>Ordering comes from {@code pageable} and only from there. Nothing in
     * this query sorts, so a caller that wants a defined page order — and paging
     * is unstable without one — has to supply it.</p>
     *
     * <p>The {@code CAST(... AS String)} is not decoration. A parameter whose
     * only other use is {@code IS NULL} and the inside of a {@code CONCAT} has no
     * comparison telling Hibernate what type it is, and it binds such a parameter
     * as {@code bytea} — which is how this query first failed, with
     * {@code function lower(bytea) does not exist}. The role is typed by
     * {@code u.role = :role}; the search term needs saying out loud.</p>
     *
     * @param role     restrict to this role, or null for every role
     * @param search   term matched against name and address, or null for no
     *                 search. A blank string is not the same as null here: it
     *                 matches every row through {@code LIKE '%%'}, so pass null
     *                 to mean "unfiltered"
     * @param pageable page and ordering request
     * @return one page of matching accounts
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE (:role IS NULL OR u.role = :role)
            AND (
                :search IS NULL
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
            )
            """)
    Page<User> findUsers(
            @Param("role") UserRole role,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT u.role FROM User u WHERE u.id = :userId")
    Optional<UserRole> findRoleByUserId(@Param("userId") UUID userId);
}
