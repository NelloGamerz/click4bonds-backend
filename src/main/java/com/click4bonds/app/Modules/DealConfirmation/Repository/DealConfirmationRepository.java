package com.click4bonds.app.Modules.DealConfirmation.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;

public interface DealConfirmationRepository
        extends JpaRepository<DealConfirmation, UUID> {

    Optional<DealConfirmation> findByDealReference(String dealReference);

    /**
     * A deal by id, scoped to the employee who created it — the same scoping the
     * listing and the document download use, so one employee cannot price
     * another's deal.
     *
     * <p>The bond is fetched eagerly because the caller prices against it after
     * the lookup.</p>
     */
    @EntityGraph(attributePaths = { "bond" })
    Optional<DealConfirmation> findByIdAndCreatedBy_Id(UUID id, UUID employeeId);

    /**
     * One page of the deals a staff account created, optionally filtered by a
     * term matched against the reference, the client, the bond and the ISIN.
     *
     * <p>Scoped to {@code createdBy} in the query so one employee cannot page
     * through another's deals. {@code search} is a caller-supplied trim; pass
     * null to mean "unfiltered" (a blank string would match every row through
     * {@code LIKE '%%'} all the same, but the distinction is deliberate here).</p>
     *
     * <p>The bond and client are fetched eagerly because the summary reads both
     * and the caller maps after the page has been loaded.</p>
     */
    @EntityGraph(attributePaths = { "bond", "client" })
    @Query("""
            SELECT d
            FROM DealConfirmation d
            LEFT JOIN d.client c
            LEFT JOIN d.bond b
            WHERE d.createdBy.id = :employeeId
            AND (
                :search IS NULL
                OR LOWER(d.dealReference) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(d.isin) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
            )
            """)
    Page<DealConfirmation> findCreatedBy(
            @Param("employeeId") UUID employeeId,
            @Param("search") String search,
            Pageable pageable);

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
     * The same lookup for a back-office deal, scoped to the employee who created
     * it so one employee cannot download another's letter.
     */
    Optional<DealConfirmation> findByDealReferenceAndCreatedBy_Id(
            String dealReference,
            UUID employeeId);

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
                   d.documentR2Path = :documentR2Path,
                   d.documentGeneratedAt = :documentGeneratedAt,
                   d.updatedAt = :documentGeneratedAt
             WHERE d.id = :dealId
            """)
    int recordDocument(
            @Param("dealId") UUID dealId,
            @Param("status") DealConfirmationStatus status,
            @Param("documentR2Path") String documentR2Path,
            @Param("documentGeneratedAt") Instant documentGeneratedAt);
}
