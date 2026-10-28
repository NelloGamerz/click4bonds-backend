package com.click4bonds.app.Modules.Party.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The PAN a party has submitted, and where its verification stands.
 *
 * <p>One record per party, because the identity a PAN represents is one per
 * party for the party types this platform supports. The uniqueness is on
 * {@code party_id}, not on the PAN value: two legal entities can never share a
 * PAN, but the stored value is ciphertext and comparing it is not how identity
 * is established.</p>
 *
 * <p>{@link #panNumber} holds AES-GCM ciphertext (base64 of IV + ciphertext +
 * tag), never plaintext. It is written and read only through
 * {@code PanDetailsService} and {@code SecretCipher}; nothing outside those
 * classes should log, compare or serialize it. API responses carry a masked
 * form.</p>
 *
 * <p>{@link #status} is this record's verification state. It is intentionally
 * separate from {@code UserVerification.panStatus}, which remains the onboarding
 * channel state; neither is derived from the other here. A submitted PAN starts
 * {@code PENDING} and is never {@code VERIFIED} merely because it was
 * submitted.</p>
 */
@Entity
@Table(name = "pan_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PanDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    /** Ciphertext. Never log or return this value. */
    @Column(name = "pan_number", nullable = false, columnDefinition = "text")
    private String panNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.NOT_STARTED;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
