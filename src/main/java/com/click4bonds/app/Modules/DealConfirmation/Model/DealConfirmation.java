package com.click4bonds.app.Modules.DealConfirmation.Model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.User.Model.User;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A confirmed purchase of a number of bond lots.
 *
 * <p>Created directly once the requested quantity has been reserved from the
 * bond's inventory — there is no approval workflow. The reservation and this
 * row are written in one transaction, so a deal that exists always has its
 * units already deducted from {@link Bond#getRemainingQuantity()}.</p>
 *
 * <p><strong>Why some fields are copied rather than only referenced.</strong>
 * This row is the buyer's confirmation document, so the values it is built from
 * are frozen here at creation time: the ISIN and the price per unit can later be
 * edited on the bond (or the bond cancelled) without rewriting what the customer
 * was sold. The {@link #bond} association is kept alongside them for
 * traceability, not as the source of those values.</p>
 */
@Entity
@Table(name = "deal_confirmations", indexes = {
        @Index(name = "idx_deal_reference", columnList = "deal_reference", unique = true),
        @Index(name = "idx_deal_customer", columnList = "customer_id"),
        @Index(name = "idx_deal_bond", columnList = "bond_id"),
        @Index(name = "idx_deal_isin", columnList = "isin"),
        @Index(name = "idx_deal_status", columnList = "status"),
        @Index(name = "idx_deal_created_at", columnList = "createdAt")
}, uniqueConstraints = {
        /*
         * Backstop for the idempotency check in DealConfirmationService: a
         * client that retries a request with the same Idempotency-Key can never
         * end up with two deals, even if both requests are in flight at once.
         * NULL keys are not compared by PostgreSQL, so requests that send no key
         * are unaffected.
         */
        @UniqueConstraint(
                name = "uk_deal_customer_idempotency_key",
                columnNames = { "customer_id", "idempotency_key" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Customer-facing reference, unique across all deals.
     *
     * <p>Format: {@code DC-YYYYMMDD-000001}, where the last part is a daily
     * sequence allocated from the database by {@code DealReferenceGenerator}.
     * The unique index above is what actually guarantees it.</p>
     */
    @Column(name = "deal_reference", nullable = false, unique = true, length = 32)
    private String dealReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    /** Snapshot of the bond's ISIN at the time of purchase. */
    @Column(nullable = false, length = 12)
    private String isin;

    @Column(name = "quantity_per_lot", nullable = false)
    private Long quantityPerLot;

    @Column(name = "number_of_lots", nullable = false)
    private Long numberOfLots;

    /**
     * {@code quantityPerLot * numberOfLots}, computed by the server.
     *
     * <p>Stored rather than derived on read because it is the quantity that was
     * actually reserved from inventory, and it is what the confirmation document
     * has to show years later.</p>
     */
    @Column(name = "total_quantity", nullable = false)
    private Long totalQuantity;

    /**
     * Clean price per unit charged for this deal.
     *
     * <p>NULL when the bond has no price recorded — the same nullable price the
     * bond carries, captured as it was at purchase time. Money is always
     * {@link BigDecimal}, never a floating point type.</p>
     */
    @Column(name = "price_per_unit", precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    /** {@code pricePerUnit * totalQuantity}; NULL when the price is unknown. */
    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DealConfirmationStatus status = DealConfirmationStatus.CREATED;

    /**
     * Optional client-supplied key that makes this endpoint idempotent.
     *
     * <p>Sent as the {@code Idempotency-Key} header by clients that retry, so a
     * double-click or a network retry replays the original deal instead of
     * buying the bond twice. NULL when the client sent no key.</p>
     */
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    /**
     * Location of the generated confirmation document, relative to the
     * configured document storage root.
     *
     * <p>Deliberately relative rather than absolute: an absolute path would tie
     * this row to one host's directory layout, so moving the storage root or
     * mounting it elsewhere in the container would leave every historical row
     * pointing at nothing. It is resolved against the storage root when read.</p>
     *
     * <p>NULL means no document has been produced for this deal yet — either
     * generation has not run, or it failed. The deal's {@link #status} is the
     * other half of that answer.</p>
     */
    @Column(name = "document_path", length = 512)
    private String documentPath;

    /**
     * When {@link #documentPath} was written.
     *
     * <p>Kept because "which deals are stuck waiting for a document" is a
     * question about age, and the path alone cannot answer it.</p>
     */
    @Column(name = "document_generated_at")
    private Instant documentGeneratedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
