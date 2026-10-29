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
 * A bank account belonging to a party. A party may have several.
 *
 * <p>At most one of them may be the primary account. That rule is enforced in
 * {@code BankAccountService} inside a transaction and, as the concurrency
 * backstop, by the partial unique index {@code uk_bank_account_primary} on
 * {@code (party_id) WHERE is_primary}. A second concurrent claim of primary in
 * the same party fails against the index rather than leaving two primaries.</p>
 *
 * <p>{@link #accountNumber} holds AES-GCM ciphertext. It is written and read
 * only through {@code BankAccountService} and {@code SecretCipher}; responses
 * carry a masked form. The IFSC is stored and returned as-is: it identifies a
 * branch, not an account.</p>
 */
@Entity
@Table(name = "bank_accounts", indexes = {
        @Index(name = "idx_bank_account_party", columnList = "party_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    /**
     * The branch the account is held at, as the customer supplies it.
     *
     * <p>Nullable: an account can be recorded before its branch is known, and a
     * missing branch prints as an em dash on the confirmation letter rather than
     * blocking the deal.</p>
     */
    @Column(name = "branch_location")
    private String branchLocation;

    /** Ciphertext. Never log or return this value. */
    @Column(name = "account_number", nullable = false, columnDefinition = "text")
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean primaryAccount = false;

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
