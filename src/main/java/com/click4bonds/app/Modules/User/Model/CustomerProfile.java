package com.click4bonds.app.Modules.User.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.User.Enums.AgeRange;
import com.click4bonds.app.Modules.User.Enums.CommunicationLanguage;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserType;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the platform knows about a customer that it knows about no one else.
 *
 * <p>{@link User} is the identity every account has — a name, a number to sign
 * in with, a role. This is everything beyond that which only means something
 * for someone buying bonds: the answers the sign-up form collects, the consents
 * they gave, how far through KYC they have got, and the per-channel verification
 * record behind that. An admin or back-office account has none of these, and
 * this is the type that says so rather than a row of columns left null on every
 * staff account.</p>
 *
 * <p>The association is one-to-one in both directions and this side owns it:
 * {@code user_id} is a column here, not a pair of nullable columns on
 * {@code users}. That follows {@link UserVerification}, which has always held
 * its own foreign key rather than being pointed at, and it keeps the
 * exclusivity of the two profiles out of the {@code users} table — there is no
 * need for a constraint saying "exactly one of these two columns is filled in"
 * when the profiles are separate tables in the first place.</p>
 *
 * <p>Whether a customer has a record here at all is a service-layer rule, not a
 * database one: {@link com.click4bonds.app.Modules.User.Service.UserService}
 * creates exactly one profile for a {@code CUSTOMER} and refuses to create a
 * mismatched one. The database enforces only what it can — that a profile
 * belongs to one user, and that a user has at most one profile.</p>
 */
@Entity
@Table(name = "customer_profiles", indexes = {
        @Index(name = "idx_customer_profile_user", columnList = "user_id", unique = true)})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The account this profile describes.
     *
     * <p>The owning side of the association, so it is this field that has to be
     * set for a profile to persist at all: assigning
     * {@code user.setCustomerProfile(profile)} on its own leaves
     * {@code user_id} null and the insert fails against the not-null constraint.
     * Creation goes through
     * {@link com.click4bonds.app.Modules.User.Service.UserService#createUser},
     * which sets both directions, so no caller has to remember.</p>
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * The per-channel verification record: email, phone, PAN, bank account and
     * demat.
     *
     * <p>Hangs off the profile rather than off the account, because only a
     * customer is ever asked to prove these. It is created with the profile and
     * removed with it.</p>
     */
    @OneToOne(mappedBy = "customerProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserVerification verification;

    /**
     * Whether the account has cleared every verification step and is therefore
     * fully known to the platform.
     *
     * <p>Separate from {@link #onboardingStep}, which records how far the
     * customer has <em>got</em>; this records whether they are <em>done</em>.
     * The step can sit at its final value while a re-verification is
     * outstanding, so the two are not interchangeable.</p>
     *
     * <p>Typed as a wrapper, matching {@code Bond.isFlashNews}, so the getter
     * Lombok generates is {@code getIsKycCompleted} and the JSON property is
     * {@code isKycCompleted} rather than {@code kycCompleted} — which is what a
     * primitive {@code isKycCompleted} field would produce.</p>
     */
    @Column(name = "is_kyc_completed", nullable = false)
    @Builder.Default
    private Boolean isKycCompleted = false;

    /**
     * The step of KYC the customer has reached.
     *
     * <p>Every value {@link OnboardingStep} defines is a customer verification
     * channel — phone, email, PAN, bank account, demat — and it is advanced by
     * {@link com.click4bonds.app.Modules.User.Service.VerificationService} on
     * the strength of the verification record beside it. Staff accounts do not
     * onboard, which is why this is here and not on {@link User}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_step", nullable = false)
    @Builder.Default
    private OnboardingStep onboardingStep = OnboardingStep.PHONE_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_range")
    private AgeRange ageRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_communication_language")
    private CommunicationLanguage preferredCommunicationLanguage;

    @Column(name = "whatsapp_communication_consent", nullable = false)
    @Builder.Default
    private Boolean whatsappCommunicationConsent = false;

    @Column(name = "terms_accepted", nullable = false)
    @Builder.Default
    private Boolean termsAccepted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
