package com.click4bonds.app.Modules.Bond.Service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondIssuerBulkItem;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Models.Issuer;

/**
 * Writes a single row of a bulk issuer import.
 *
 * Deliberately a separate bean: {@link IssuerService} is not
 * transactional while looping, so every call here runs in its own
 * transaction and one failing row cannot roll back the others.
 */
@Service
public class IssuerBulkWriter {

    private final BondService bondService;
    private final IssuerMapper issuerMapper;
    private final IssuerService issuerService;

    /**
     * {@code issuerService} is injected lazily because it owns the bulk loop
     * that calls this bean: an eager dependency would be a constructor cycle.
     * The proxy is only used for the writes below, so deferring it costs
     * nothing.
     */
    public IssuerBulkWriter(
            BondService bondService,
            IssuerMapper issuerMapper,
            @Lazy IssuerService issuerService) {

        this.bondService = bondService;
        this.issuerMapper = issuerMapper;
        this.issuerService = issuerService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowOutcome writeRow(BondIssuerBulkItem item) {

        String isin = item.getIsin().trim().toUpperCase();

        Bond bond = bondService.findBond(isin);

        Issuer issuer = bond.getIssuer();

        /*
         * Bond has no issuer yet -> insert a new one.
         */
        if (issuer == null) {

            Issuer created = issuerMapper.apply(new Issuer(), item.getIssuer());

            issuerService.validateUnique(created, null);

            Issuer saved = issuerService.saveIssuer(created);

            link(bond, saved);

            return new RowOutcome(issuerMapper.toResponse(saved), true);
        }

        /*
         * Bond already has an issuer -> import acts as an upsert,
         * so re-importing the same sheet is idempotent.
         */
        issuerMapper.apply(issuer, item.getIssuer());

        issuerService.validateUnique(issuer, issuer.getId());

        Issuer saved = issuerService.saveIssuer(issuer);

        return new RowOutcome(issuerMapper.toResponse(saved), false);
    }

    private void link(Bond bond, Issuer issuer) {

        bond.setIssuer(issuer);

        bondService.saveBond(bond);
    }

    public record RowOutcome(IssuerResponse issuer, boolean created) {
    }
}
