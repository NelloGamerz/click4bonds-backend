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
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
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
                                   OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(b.isin) LIKE LOWER(CONCAT('%', :search, '%')))
                              AND (:isFlashNews IS NULL
                                   OR :isFlashNews = false
                                   OR b.isFlashNews = true)
                              AND (:securityType IS NULL OR b.securityType = :securityType)
                              AND (:maturityType IS NULL OR b.maturityType = :maturityType)
                              AND (:rating IS NULL OR :rating = ''
                                   OR LOWER(b.rating) = LOWER(:rating))
                        """)
        Page<Bond> searchBonds(
                        @Param("search") String search,
                        @Param("isFlashNews") Boolean isFlashNews,
                        @Param("securityType") SecurityType securityType,
                        @Param("maturityType") MaturityType maturityType,
                        @Param("rating") String rating,
                        Pageable pageable);

        /**
         * {@link #searchBonds} restricted to a single status.
         *
         * <p>Exists so the public listing can ask for {@code ACTIVE} bonds and
         * have the count come back right: the status predicate is part of the
         * query, so the {@code Page} the caller receives has a
         * {@code totalElements} of matching active bonds rather than of every
         * bond in the table. Filtering after the fact in Java could not fix the
         * count, only the rows.</p>
         *
         * <p>The catalogue's narrowings — security type, maturity type and
         * rating — are predicates here for the same reason: a {@code Page}
         * carries its counts, so a filter applied after the fact would return
         * the right rows with the wrong {@code totalElements}. Each is optional
         * and short-circuits on null, so a caller that passes none gets exactly
         * the query this method ran before they existed.</p>
         *
         * <p>{@code rating} matches the stored grade column exactly rather than
         * the agency-and-grade string {@code BondResponse.rating} shows, which
         * is why it is compared on its own and not against
         * {@code ratingAgency}. The comparison is case-insensitive.</p>
         */
        @Query("""
                            SELECT b
                            FROM Bond b
                            WHERE b.status = :status
                              AND (:search IS NULL OR :search = ''
                                   OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(b.isin) LIKE LOWER(CONCAT('%', :search, '%')))
                              AND (:isFlashNews IS NULL
                                   OR :isFlashNews = false
                                   OR b.isFlashNews = true)
                              AND (:securityType IS NULL OR b.securityType = :securityType)
                              AND (:maturityType IS NULL OR b.maturityType = :maturityType)
                              AND (:rating IS NULL OR :rating = ''
                                   OR LOWER(b.rating) = LOWER(:rating))
                        """)
        Page<Bond> searchBondsByStatus(
                        @Param("status") BondStatus status,
                        @Param("search") String search,
                        @Param("isFlashNews") Boolean isFlashNews,
                        @Param("securityType") SecurityType securityType,
                        @Param("maturityType") MaturityType maturityType,
                        @Param("rating") String rating,
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
         * <p><strong>This is the oversell guard, and it must stay in this layer
         * intact.</strong> It is deliberately a single conditional UPDATE rather
         * than a read-then-write in Java. The
         * {@code remainingQuantity >= :quantity} predicate is evaluated by the
         * database while it holds a row lock, so two concurrent buyers are
         * serialized by PostgreSQL and the loser matches zero rows instead of
         * taking the inventory negative. Reading the quantity, checking it in
         * Java and then updating would reopen exactly that race, so this
         * statement must not be "simplified" into that shape.</p>
         *
         * <p>Deal confirmation does NOT call this: a deal record states what was
         * agreed and holds no stock. Inventory is claimed by the reservation
         * step, which is where this method's future caller lives — reserving is
         * expected to happen before a draft deal exists, with the units consumed
         * once payment settles.</p>
         *
         * <p>Callers MUST check the returned row count: {@code 1} means the units
         * were reserved, {@code 0} means the bond had fewer units left than
         * requested (or its inventory is NULL / unconfigured). A {@code 0} is a
         * business outcome — the caller reports "insufficient quantity" — not an
         * error to be retried.</p>
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
