package com.click4bonds.app.Modules.Holding.Model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserRoleAssertions;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bond_holdings", indexes = {
        @Index(name = "idx_holding_customer", columnList = "customer_id"),
        @Index(name = "idx_holding_bond", columnList = "bond_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BondHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /**
     * Refuses a holding recorded for anything but a customer account.
     *
     * <p>A holding is what a purchase settles into, so it belongs to the same
     * account the order did, and the same rule applies for the same reason: the
     * owner is taken from the order rather than from the request, so a holding
     * for a staff account would mean the order it came from was placed for one
     * too.</p>
     */
    @PrePersist
    @PreUpdate
    private void assertCustomerRole() {

        UserRoleAssertions.requireCustomer(customer, "BondHolding");
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePurchasePrice;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant purchasedAt;

    @UpdateTimestamp
    private Instant updatedAt;
}