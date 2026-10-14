package com.click4bonds.app.Modules.Order.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Order.Model.BondOrder;

public interface BondOrderRepository extends JpaRepository<BondOrder, UUID> {
    Page<BondOrder> findByCustomer_Id(
            UUID customerId,
            Pageable pageable);

    /**
     * Used to replay a retried create request instead of placing a second order.
     *
     * <p>Scoped to the customer on purpose: two admins may legitimately pick the
     * same key, and one must never be handed the other's order. The same
     * scoping is what the unique index on the table enforces.</p>
     *
     * <p>The bond and customer are fetched eagerly because the caller may map
     * the result after the surrounding transaction has closed — reading a lazy
     * association then would fail.</p>
     */
    @EntityGraph(attributePaths = { "bond", "customer" })
    Optional<BondOrder> findByCustomer_IdAndIdempotencyKey(
            UUID customerId,
            String idempotencyKey);
}
