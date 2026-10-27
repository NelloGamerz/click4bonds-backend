package com.click4bonds.app.Modules.User.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * {@link AdminProfile}. Nothing here is reachable from a client-facing endpoint;
 * the only writer is the admin client surface.</p>
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
