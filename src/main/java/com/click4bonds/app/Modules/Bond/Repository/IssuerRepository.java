package com.click4bonds.app.Modules.Bond.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
