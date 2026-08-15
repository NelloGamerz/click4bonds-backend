package com.click4bonds.app.Modules.Order.Repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Order.Model.BondOrder;

public interface BondOrderRepository extends JpaRepository<BondOrder, UUID> {
    // Page<BondOrder> findByCustomerId(UUID customerId, Pageable papge);
    Page<BondOrder> findByCustomer_ClerkUserId(
            String clerkUserId,
            Pageable pageable);
}
