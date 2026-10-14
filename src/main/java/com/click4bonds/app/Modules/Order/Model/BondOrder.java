package com.click4bonds.app.Modules.Order.Model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Enums.BondOrderStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.User.Model.User;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bond_orders", indexes = {
        @Index(name = "idx_order_customer", columnList = "customer_id"),
        @Index(name = "idx_order_bond", columnList = "bond_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_created_at", columnList = "createdAt")
}, uniqueConstraints = {
        /*
         * Backstop for the idempotency check in BondOrderService: a caller that
         * retries a create request with the same Idempotency-Key can never end
         * up with two orders, even if both requests are in flight at once. NULL
         * keys are not compared by PostgreSQL, so requests that send no key are
         * unaffected.
         */
        @UniqueConstraint(
                name = "uk_order_customer_idempotency_key",
                columnNames = { "customer_id", "idempotency_key" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BondOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    /**
     * Units this order is for — {@code numberOfLots * bond.lotSize}, worked out
     * by the server when the order was placed.
     *
     * <p>Stored in units rather than lots because units are what the inventory
     * reservation and the customer's holding are counted in. The lot count the
     * caller sent is not kept: the lot size is a property of the bond and can be
     * edited, so re-deriving lots later from a stored lot count could disagree
     * with the units actually being taken.</p>
     */
    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BondOrderStatus status = BondOrderStatus.PENDING;

    /**
     * Optional client-supplied key that makes order creation idempotent.
     *
     * <p>Sent as the {@code Idempotency-Key} header by a caller that may retry,
     * so a double click or a network retry replays the original order instead of
     * placing a second one for the same bond and quantity. NULL when the caller
     * sent no key — the same convention {@code DealConfirmation} uses.</p>
     */
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}