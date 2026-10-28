package com.click4bonds.app.Modules.Party.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Party.Enums.PartyType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A party: an individual or legal entity the business holds financial records
 * against.
 *
 * <p>Deliberately not an account. There are no credentials, no role and no
 * status here, and no association to {@code User}. A {@code Client} recorded by
 * the back office becomes a party without becoming a login, and a registered
 * customer acquires a party without the party acquiring a login. The contact
 * fields are the business's record of how to reach the party, not the
 * authentication identity — a {@code User} keeps its own email and number for
 * signing in.</p>
 *
 * <p>Kids hang off this entity rather than the other way round: {@code PanDetails},
 * {@code BankAccount} and {@code DematAccount} each own a foreign key to the
 * party. There are no collections here, so serializing a party cannot walk into
 * financial records by accident, and none of those records is deleted by a
 * cascade from this side.</p>
 */
@Entity
@Table(name = "parties", indexes = {
        @Index(name = "idx_party_legal_name", columnList = "legal_name")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false)
    private PartyType partyType;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(columnDefinition = "text")
    private String address;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
