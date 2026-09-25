package com.click4bonds.app.Modules.DealConfirmation.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;

public interface DealConfirmationRepository
        extends JpaRepository<DealConfirmation, UUID> {

    Optional<DealConfirmation> findByDealReference(String dealReference);

    /**
     * Looks up a deal by reference, but only for the customer it belongs to.
     *
     * <p>Used to serve a deal's generated document. Scoping the query to the
     * customer rather than fetching the deal and comparing owners afterwards
     * means another customer's reference is indistinguishable from one that does
     * not exist — the caller gets "not found" either way, so the endpoint cannot
     * be used to probe which references are real.</p>
     */
    Optional<DealConfirmation> findByDealReferenceAndCustomer_Id(
            String dealReference,
            UUID customerId);

    /**
     * Used to replay a retried request instead of buying the bond twice.
     *
     * <p>{@code userId} is {@link com.click4bonds.app.Modules.User.Model.User#getId()},
     * the user's canonical id and the only identifier this application uses —
     * there is no second, external-provider key to resolve through.</p>
     *
     * <p>Scoped to the customer on purpose: two customers may legitimately pick
     * the same key, and one customer must never be handed another's deal.</p>
     *
     * <p>The bond and customer are fetched eagerly because the replay happens
     * outside a transaction — mapping it afterwards must not trigger a lazy
     * load.</p>
     */
    @EntityGraph(attributePaths = { "bond", "customer" })
    Optional<DealConfirmation> findByCustomer_IdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey);

    /**
     * Records the generated document against a deal, in one statement.
     *
     * <p>Deliberately a bulk UPDATE rather than a load-then-save. The document
     * step runs after the deal's transaction has committed, and the interface
     * contract forbids it from reading the database or touching a lazy
     * association; a single statement keyed on the primary key reads nothing and
     * loads no entity. It also cannot clobber a concurrent change to any other
     * column, which a full-row save would.</p>
     *
     * <p><strong>{@code updated_at} is set explicitly.</strong>
     * {@code @UpdateTimestamp} is a Hibernate entity-lifecycle hook and does not
     * fire for a bulk UPDATE that bypasses the persistence context. Omitting it
     * here would silently stop {@code updatedAt} tracking this change.</p>
     *
     * @return 1 when the deal was updated, 0 when no deal has that id
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DealConfirmation d
               SET d.status = :status,
                   d.documentPath = :documentPath,
                   d.documentGeneratedAt = :documentGeneratedAt,
                   d.updatedAt = :documentGeneratedAt
             WHERE d.id = :dealId
            """)
    int recordDocument(
            @Param("dealId") UUID dealId,
            @Param("status") DealConfirmationStatus status,
            @Param("documentPath") String documentPath,
            @Param("documentGeneratedAt") Instant documentGeneratedAt);
}
