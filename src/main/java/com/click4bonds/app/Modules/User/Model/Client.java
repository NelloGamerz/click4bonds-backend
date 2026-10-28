package com.click4bonds.app.Modules.User.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Party.Model.Party;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A client of the business, as recorded by the back office.
 *
 * <p>Deliberately not an account: a client is a party the business deals with,
 * not someone who signs in, so this holds no role, status or credentials and has
 * no association with {@link User}, {@link CustomerProfile} or
 * {@link AdminProfile}. The link to a {@link Party} gives the back office a
 * place to keep the party's financial records without turning the client into a
 * login. Nothing here is reachable from a client-facing endpoint; the only
 * writer is the admin client surface.</p>
 *
 * <p>The client-specific columns are preserved alongside the party. They are
 * the record the back office works with; the party is the shared identity the
 * financial records hang off. An explicit, authorized linking step is the only
 * way a client comes to share a party with a registered customer.</p>
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The party this client record describes.
     *
     * <p>Owned by this side, like every other financial relationship: there is
     * no collection on {@link Party}. No cascade, so removing a client never
     * removes a party that another client could share.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    private String email;

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
