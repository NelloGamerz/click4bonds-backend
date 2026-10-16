package com.click4bonds.app.Modules.User.Model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.click4bonds.app.Modules.User.Enums.AdminModule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the platform knows about an admin or back-office account that it knows
 * about no one else.
 *
 * <p>Covers both internal roles — {@code ADMIN} and {@code EMPLOYEE} — because
 * the two are the same kind of thing: a member of staff with a set of module
 * permissions. They differ in how much those
 * permissions constrain them, not in what is stored, and
 * {@link #accessConfig} is read only for an {@code EMPLOYEE}; an {@code ADMIN}
 * is unrestricted by design, which keeps a misconfigured grant from locking an
 * administrator out of the surface that would let them repair it.</p>
 *
 * <p>Holds no KYC, no consents and no onboarding step, because a staff account
 * has none of those — the counterpart for customers is
 * {@link CustomerProfile}, and a given account has exactly one of the two.
 * Which one is a service-layer rule enforced in
 * {@link com.click4bonds.app.Modules.User.Service.UserService}.</p>
 */
@Entity
@Table(name = "admin_profiles", indexes = {
        @Index(name = "idx_admin_profile_user", columnList = "user_id", unique = true)})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The account this profile describes.
     *
     * <p>The owning side of the association, so it is this field that has to be
     * set for a profile to persist: assigning
     * {@code user.setAdminProfile(profile)} alone leaves {@code user_id} null
     * and the insert fails.</p>
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Whether the staff member has confirmed the address their account was
     * created with.
     *
     * <p>A staff account is created by an administrator naming an address, and
     * nobody has proven that address belongs to the person it names until the
     * person signs in with it. Signing in with an emailed code is that proof, so
     * this is set when the account first signs in by email — the same rule the
     * customer flow applies to an address, expressed as a single flag because a
     * staff account has one address to confirm and none of the other channels a
     * customer is asked for.</p>
     *
     * <p>False until then, which is the truthful state for a freshly invited
     * account: the address is what the administrator typed, not what anyone has
     * demonstrated they can receive mail at.</p>
     */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * The modules this account may use.
     *
     * <p>Stored as JSONB and read on every admin request by
     * {@link com.click4bonds.app.Modules.Admin.Service.AdminPermissionService},
     * so a permission withdrawn here stops working at the next request rather
     * than at the next token refresh. That is the whole reason the permissions
     * live in the database and are not carried in the access token: a token is a
     * statement about the past, and authorization is a question about now.</p>
     *
     * <p>An empty set is a valid, and the starting, state: it grants nothing. It
     * is what every existing staff account gets, because no permission has ever
     * been recorded for one — the column did not exist.</p>
     *
     * <p><strong>Replace this set; do not mutate it.</strong> The whole
     * collection is serialized as a single JSONB value, so Hibernate compares it
     * by identity when deciding whether the row is dirty. Adding to the existing
     * set and saving is silently a no-op; assign a new set instead. Nothing
     * outside {@code AdminUserService} should touch this field.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "access_config", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Set<AdminModule> accessConfig = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * @param module module to test
     * @return {@code true} when this account has been granted the module
     */
    public boolean allows(AdminModule module) {

        return module != null && accessConfig != null && accessConfig.contains(module);
    }
}
