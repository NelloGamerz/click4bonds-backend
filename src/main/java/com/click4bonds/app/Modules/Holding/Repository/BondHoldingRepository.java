package com.click4bonds.app.Modules.Holding.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.click4bonds.app.Modules.Holding.Model.BondHolding;

public interface BondHoldingRepository
                extends JpaRepository<BondHolding, UUID> {

        Page<BondHolding> findByCustomerId(
                        UUID customerId,
                        Pageable pageable);

        Page<BondHolding> findByCustomer_ClerkUserId(String customerId, Pageable pageable);

        Optional<BondHolding> findByCustomerIdAndBondId(
                        UUID customerId,
                        UUID bondId);

        long countByCustomerId(UUID customerId);

        @Query("""
                            SELECT COALESCE(
                                SUM(h.quantity * h.averagePurchasePrice),
                                0
                            )
                            FROM BondHolding h
                            WHERE h.customer.id = :customerId
                        """)
        BigDecimal calculatePortfolioValue(
                        @Param("customerId") UUID customerId);
}
