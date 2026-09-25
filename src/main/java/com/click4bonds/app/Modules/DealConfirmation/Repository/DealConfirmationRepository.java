package com.click4bonds.app.Modules.DealConfirmation.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;

public interface DealConfirmationRepository
        extends JpaRepository<DealConfirmation, UUID> {

    Optional<DealConfirmation> findByDealReference(String dealReference);

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
}
