package com.click4bonds.app.Modules.Bond.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
                SELECT b
                FROM Bond b
                WHERE LOWER(b.isin) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Bond> searchBonds(
            @Param("search") String search,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT b
                FROM Bond b
                WHERE b.id = :id
            """)
    Optional<Bond> findByIdForUpdate(@Param("id") UUID id);
}
