package com.click4bonds.app.Modules.Bond.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.click4bonds.app.Modules.Bond.Models.Issuer;

public interface IssuerRepository extends JpaRepository<Issuer, UUID> {

    Optional<Issuer> findByIssuerCode(String issuerCode);

    Optional<Issuer> findByCin(String cin);

    Optional<Issuer> findByLei(String lei);

    Optional<Issuer> findByNameIgnoreCase(String name);

    boolean existsByIssuerCode(String issuerCode);

    boolean existsByCin(String cin);

    boolean existsByLei(String lei);

    boolean existsByIssuerCodeAndIdNot(String issuerCode, UUID id);

    boolean existsByCinAndIdNot(String cin, UUID id);

    boolean existsByLeiAndIdNot(String lei, UUID id);

    List<Issuer> findByIssuerCodeIn(Collection<String> issuerCodes);

    // =========================================================
    // CURSOR PAGINATION
    // =========================================================
    //
    // Issuers are ordered by lower(name), then by id to break ties:
    // two issuers can share a name, but never an id.
    //
    // The cursor is the (name, id) pair of the last row of the
    // previous page, so each page resumes exactly after it. This
    // stays correct while rows are inserted or deleted, which
    // offset paging does not guarantee.
    //
    // The Pageable carries only the limit and is always unsorted,
    // so the ORDER BY below is the single source of ordering.

    /**
     * First page: no cursor yet.
     *
     * @param pattern LIKE pattern over lower(name), already escaped.
     */
    @Query("""
            SELECT i
            FROM Issuer i
            WHERE LOWER(i.name) LIKE :pattern
            ORDER BY LOWER(i.name) ASC, i.id ASC
            """)
    List<Issuer> findPageBySearch(
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Next page: everything ordered after {@code lastName} /
     * {@code lastId}.
     *
     * @param lastName lower-cased name from the cursor.
     */
    @Query("""
            SELECT i
            FROM Issuer i
            WHERE LOWER(i.name) LIKE :pattern
              AND (LOWER(i.name) > :lastName
                   OR (LOWER(i.name) = :lastName AND i.id > :lastId))
            ORDER BY LOWER(i.name) ASC, i.id ASC
            """)
    List<Issuer> findPageBySearchAfter(
            @Param("pattern") String pattern,
            @Param("lastName") String lastName,
            @Param("lastId") UUID lastId,
            Pageable pageable);
}
