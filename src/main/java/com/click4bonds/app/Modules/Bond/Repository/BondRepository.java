package com.click4bonds.app.Modules.Bond.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import jakarta.persistence.LockModeType;

public interface BondRepository extends JpaRepository<Bond, UUID> {

        Optional<Bond> findByIsin(String isin);

        boolean existsByIsin(String isin);

        Page<Bond> findByStatus(
                        BondStatus status,
                        Pageable pageable);

        Page<Bond> findByNameContainingIgnoreCase(
                        String name,
                        Pageable pageable);

        Page<Bond> findByStatusAndNameContainingIgnoreCase(
                        BondStatus status,
                        String name,
                        Pageable pageable);

        long countByStatus(BondStatus status);

        // @Query("""
        // SELECT b
        // FROM Bond b
        // WHERE LOWER(b.isin) LIKE LOWER(CONCAT('%', :search, '%'))
        // OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
        // """)
        // Page<Bond> searchBonds(
        // @Param("search") String search,
        // @Param("isFlashNews") Boolean isFlashNews,
        // Pageable pageable);

        // @Query("""
        // SELECT b
        // FROM Bond b
        // WHERE (:search IS NULL OR :search = ''
        // OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))
        // AND (:isFlashNews IS NULL OR b.isFlashNews = :isFlashNews)
        // """)
        // Page<Bond> searchBonds(
        // @Param("search") String search,
        // @Param("isFlashNews") Boolean isFlashNews,
        // Pageable pageable);

        @Query("""
                            SELECT b
                            FROM Bond b
                            WHERE (:search IS NULL OR :search = ''
                                   OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))
                              AND (:isFlashNews IS NULL
                                   OR :isFlashNews = false
                                   OR b.isFlashNews = true)
                        """)
        Page<Bond> searchBonds(
                        @Param("search") String search,
                        @Param("isFlashNews") Boolean isFlashNews,
                        Pageable pageable);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                            SELECT b
                            FROM Bond b
                            WHERE b.id = :id
                        """)
        Optional<Bond> findByIdForUpdate(@Param("id") UUID id);

        /**
         * Reserves (deducts) {@code quantity} units from a bond's inventory.
         *
         * <p>This is deliberately a single conditional UPDATE rather than a
         * read-then-write in Java. The {@code remainingQuantity >= :quantity}
         * predicate is evaluated by the database while it holds a row lock, so
         * two concurrent buyers are serialized by PostgreSQL and the loser
         * matches zero rows instead of taking the inventory negative.</p>
         *
         * <p>Callers MUST check the returned row count: {@code 1} means the units
         * were reserved, {@code 0} means the bond had fewer units left than
         * requested (or its inventory is NULL / unconfigured).</p>
         *
         * @param id       bond whose inventory is being reserved
         * @param quantity units to deduct; must be greater than zero
         * @return number of rows updated — always 0 or 1
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                            UPDATE Bond b
                            SET b.remainingQuantity = b.remainingQuantity - :quantity
                            WHERE b.id = :id
                              AND b.remainingQuantity >= :quantity
                        """)
        int reserveQuantity(
                        @Param("id") UUID id,
                        @Param("quantity") Long quantity);
}
