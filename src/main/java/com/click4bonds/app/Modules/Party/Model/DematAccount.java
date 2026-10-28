package com.click4bonds.app.Modules.Party.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Party.Enums.Depository;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A demat account belonging to a party. A party may have several.
 *
 * <p>No uniqueness constraint is imposed on the depository identifiers. A demat
 * account may be held jointly or by a corporate entity, and the same identifier
 * pair can legitimately appear on more than one party's record depending on how
 * the business models the holders. Identifier formats are validated only for
 * being present and of a plausible length — the depository, not this
 * application, is the authority on the format for a given account type.</p>
 */
@Entity
@Table(name = "demat_accounts", indexes = {
        @Index(name = "idx_demat_account_party", columnList = "party_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DematAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Depository depository;

    @Column(name = "dp_id", nullable = false, length = 20)
    private String dpId;

    @Column(name = "client_id", nullable = false, length = 20)
    private String clientId;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.NOT_STARTED;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
