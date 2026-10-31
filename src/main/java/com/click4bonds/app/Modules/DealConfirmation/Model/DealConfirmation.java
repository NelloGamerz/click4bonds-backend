package com.click4bonds.app.Modules.DealConfirmation.Model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
import com.click4bonds.app.Modules.Party.Enums.Depository;
import com.click4bonds.app.Modules.User.Model.Client;
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
 * The deal information for a purchase of a number of bond lots.
 *
 * <p>This row records what was agreed — quantities, the price as it stood, the
 * reference the customer quotes — and its {@link #status} is the deal's
 * progress. Units are reserved in the same transaction that writes the row (see
 * {@code DealConfirmationWriter}); a cancelled deal returns them, a confirmed
 * one keeps them. A confirmed or cancelled deal is final: nothing on it may be
 * changed afterwards.</p>
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
        @Index(name = "idx_deal_client", columnList = "client_id"),
        @Index(name = "idx_deal_created_by", columnList = "created_by"),
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
     * <p>Format: {@code 2026/S/OCT/144}, where the last part is a monthly
     * sequence allocated from the database by {@code DealReferenceGenerator}.
     * The unique index above is what actually guarantees it.</p>
     */
    @Column(name = "deal_reference", nullable = false, unique = true, length = 32)
    private String dealReference;

    /**
     * The account that bought the bond, for a deal a customer placed.
     *
     * <p>Null for a deal an employee created on a client's behalf; exactly one of
     * this and {@link #client} is set.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    /**
     * The client the deal was struck for, for a back-office deal.
     *
     * <p>Null for a customer-placed deal. A client is not an application account,
     * so it cannot be recorded as {@link #customer}.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    /**
     * The staff account that created this deal, for a back-office deal.
     *
     * <p>Null for a deal a customer placed through the app — there is no employee
     * behind it. Set for every deal created through {@code AdminDealController},
     * which is what lets an employee list the deals they created.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /**
     * The date the deal was struck, when the back office chose one.
     *
     * <p>Null for a customer-placed deal, whose deal date is its creation date.
     * Set for a back-office deal so the confirmation letter prints the date the
     * employee entered rather than the moment the row was written.</p>
     */
    @Column(name = "deal_date")
    private LocalDate dealDate;

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
     * <p>Stored rather than derived on read because it is the quantity this deal
     * is for, and it is what the confirmation document has to show years later.
     * It is the deal's own figure — it reflects no inventory movement.</p>
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

    /**
     * The annual yield this deal was priced at, as a percentage.
     *
     * <p>Deal-scoped: an employee can recalculate it for this deal alone and the
     * bond's own quoted yield is left untouched. NULL until a calculation is
     * saved, which is when the deal carries no yield of its own.</p>
     */
    @Column(name = "annual_ytm", precision = 19, scale = 4)
    private BigDecimal annualYtm;

    /** The semi-annual equivalent of {@link #annualYtm}, as a percentage. */
    @Column(name = "semi_ytm", precision = 19, scale = 4)
    private BigDecimal semiYtm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DealConfirmationStatus status = DealConfirmationStatus.CREATED;

    /*
     * The inputs the confirmation letter is built from, captured on the deal
     * because a back-office letter is generated only once the deal is confirmed
     * — long after the request that carried them. Null on a customer deal for
     * every field but the side, which is SELL.
     */

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", length = 20)
    private DeliveryMode deliveryMode;

    @Column(name = "settlement_number", length = 64)
    private String settlementNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private DealSide side = DealSide.SELL;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Depository depository;

    /** The counterparty bank account chosen for the letter, or null. */
    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    /** The counterparty demat account chosen for the letter, or null. */
    @Column(name = "demat_account_id")
    private UUID dematAccountId;

    /** Whether the letter is emailed to the client once it is generated. */
    @Column(name = "send_email", nullable = false)
    @Builder.Default
    private boolean sendEmail = false;

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
     * Address of the generated confirmation document in the object store, of the
     * form {@code https://<account-id>.r2.cloudflarestorage.com/<bucket>/2026/09/<dealReference>.pdf}.
     *
     * <p><strong>An address, not a key and not a presigned URL.</strong> It is
     * readable and pasteable — an operator looking at a row can find the letter
     * without knowing how the application is configured — and it carries no
     * signature, so it never expires. Three alternatives were rejected: a bare
     * key, because it means nothing without the store's configuration; a
     * presigned URL, because it stops working; and a public custom-domain URL,
     * because the bucket is private and every stored link would be dead.</p>
     *
     * <p><strong>It pins the endpoint that built it.</strong> That is the cost of
     * the readability, and it is worth knowing before changing accounts: a row
     * written against one endpoint names that endpoint for as long as it exists.
     * {@code R2ObjectStore.keyFor} refuses to resolve an address whose host or
     * bucket is not the configured one, so a move surfaces as a clear failure
     * rather than as a read from the wrong bucket.</p>
     *
     * <p>Widened from 512 to 1024 characters when it became an address: it now
     * carries an endpoint and a bucket as well as a key.</p>
     *
     * <p>NULL means no document has been produced for this deal yet — either
     * generation has not run, or it failed. The deal's {@link #status} is the
     * other half of that answer.</p>
     */
    @Column(name = "document_r2_path", length = 1024)
    private String documentR2Path;

    /**
     * When {@link #documentR2Path} was written.
     *
     * <p>Kept because "which deals are stuck waiting for a document" is a
     * question about age, and the address alone cannot answer it.</p>
     */
    @Column(name = "document_generated_at")
    private Instant documentGeneratedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
