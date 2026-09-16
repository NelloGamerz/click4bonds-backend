package com.click4bonds.app.Modules.Bond.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondIssuerBulkItem;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Models.Issuer;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Bond.Repository.IssuerRepository;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Writes a single row of a bulk issuer import.
 *
 * Deliberately a separate bean: {@link IssuerService} is not
 * transactional while looping, so every call here runs in its own
 * transaction and one failing row cannot roll back the others.
 */
@Service
@RequiredArgsConstructor
public class IssuerBulkWriter {

    private final IssuerRepository issuerRepository;
    private final BondRepository bondRepository;
    private final IssuerMapper issuerMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowOutcome writeRow(BondIssuerBulkItem item) {

        String isin = item.getIsin().trim().toUpperCase();

        Bond bond = bondRepository.findByIsin(isin)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + isin));

        Issuer issuer = bond.getIssuer();

        /*
         * Bond has no issuer yet -> insert a new one.
         */
        if (issuer == null) {

            Issuer created = issuerMapper.apply(new Issuer(), item.getIssuer());

            issuerMapper.validateUnique(created, null);

            Issuer saved = issuerRepository.save(created);

            link(bond, saved);

            return new RowOutcome(issuerMapper.toResponse(saved), true);
        }

        /*
         * Bond already has an issuer -> import acts as an upsert,
         * so re-importing the same sheet is idempotent.
         */
        issuerMapper.apply(issuer, item.getIssuer());

        issuerMapper.validateUnique(issuer, issuer.getId());

        Issuer saved = issuerRepository.save(issuer);

        return new RowOutcome(issuerMapper.toResponse(saved), false);
    }

    private void link(Bond bond, Issuer issuer) {

        bond.setIssuer(issuer);

        bondRepository.save(bond);
    }

    public record RowOutcome(IssuerResponse issuer, boolean created) {
    }
}
