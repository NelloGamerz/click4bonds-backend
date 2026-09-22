package com.click4bonds.app.Modules.User.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;

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
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_mobile_number", columnList = "mobileNumber", unique = true),
        @Index(name = "idx_user_role", columnList = "role")
})
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

    private String profileImage;

    // @Column(nullable = false)
    // @Builder.Default
    // private Boolean onboardingCompleted = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OnboardingStep onboardingStep = OnboardingStep.EMAIL_VERIFICATION;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserVerification verification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Whether the account has cleared every verification step — PAN and bank
     * account included — and is therefore fully known to the platform.
     *
     * <p>Separate from {@link #onboardingStep}, which records how far the user
     * has <em>got</em>; this records whether they are <em>done</em>. The step
     * can sit at its final value while a re-verification is outstanding, so the
     * two are not interchangeable.</p>
     *
     * <p>Typed as a wrapper, matching {@code Bond.isFlashNews}, so the getter
     * Lombok generates is {@code getIsKycCompleted} and the JSON property is
     * {@code isKycCompleted} rather than {@code kycCompleted} — which is what a
     * primitive {@code isKycCompleted} field would produce.</p>
     */
    @Column(name = "is_kyc_completed", nullable = false)
    @Builder.Default
    private Boolean isKycCompleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
