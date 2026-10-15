package com.click4bonds.app.Modules.User.Model;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person using the platform.
 *
 * <p>{@link #id} is the canonical identity of a user, everywhere. It is what a
 * token's subject carries, what the customer and creator relationships on other
 * entities point at, and what any external system integrating with this one is
 * given. There is deliberately no second identifier column: a parallel
 * external-provider key is exactly what used to live here, and its removal is
 * the point of this class's current shape.</p>
 *
 * <p>What this class holds is the part of an account that is true regardless of
 * what the account is for: who they are, how they sign in, and what they are
 * allowed to be. What is true only of a customer — KYC progress, the consents
 * the sign-up form collects, the verification record — lives on
 * {@link CustomerProfile}, and what is true only of a member of staff lives on
 * {@link AdminProfile}. A given account has exactly one of those two and never
 * both; which one is decided by {@link #role} and enforced where accounts are
 * created and where roles change, in
 * {@link com.click4bonds.app.Modules.User.Service.UserService}.</p>
 *
 * <p>The profiles are separate tables rather than columns here on purpose. With
 * the fields inline, every consumer of an account read a record that was mostly
 * null for the role it was looking at, and there was nowhere to put a field
 * that only one role has without making it null for every other. Splitting them
 * makes "a customer has no employee code" a fact about the type rather than a
 * convention about nulls.</p>
 */
@Entity
@Table(name = "users", indexes = {@Index(name = "idx_user_email", columnList = "email", unique = true), @Index(name = "idx_user_mobile_number", columnList = "mobileNumber", unique = true), @Index(name = "idx_user_role", columnList = "role")})
// @Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Contact and login address.
     *
     * <p>Nullable by necessity: signing in happens with a phone number, so an
     * account can exist before it has an address, and the email verification
     * step is what fills this in. The unique index still holds — PostgreSQL
     * treats nulls as distinct, so any number of accounts may be waiting on an
     * address while no two may share one.</p>
     */
    @Column(unique = true)
    private String email;

    /**
     * Phone number the account signs in with, in the canonical form the OTP
     * module normalises submissions to. Unique, so one number is one account.
     */
    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    private String firstName;

    private String lastName;

    /**
     * The customer record, for an account that has one.
     *
     * <p>Null for an admin or back-office account, which is not an error state
     * but the point of the split. Cascade and orphan removal are both set, so
     * the profile is written and removed with its account rather than needing a
     * second call that a caller could forget.</p>
     *
     * <p>The inverse side of the association — {@link CustomerProfile#user}
     * owns the foreign key — so assigning this alone persists nothing. Creation
     * goes through
     * {@link com.click4bonds.app.Modules.User.Service.UserService#createUser},
     * which sets both sides.</p>
     */
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private CustomerProfile customerProfile;

    /**
     * The staff record, for an account that has one.
     *
     * <p>Null for a customer, for the same reason
     * {@link #customerProfile} is null for staff. Holds the module permissions
     * an {@code EMPLOYEE} is constrained by.</p>
     */
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private AdminProfile adminProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
