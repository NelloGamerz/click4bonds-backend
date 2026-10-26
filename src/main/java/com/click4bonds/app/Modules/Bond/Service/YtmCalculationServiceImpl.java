package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class YtmCalculationServiceImpl implements YtmCalculationService {

    private final BondCashFlowService bondCashFlowService;
    private final XirrCalculator xirrCalculator;
    private final BondService bondService;

    /**
     * {@code bondService} is injected lazily to break a genuine cycle: this
     * engine is used by {@link BondValuationService}, which {@link BondService}
     * calls on create and update, and the calculated yield is persisted through
     * {@code BondService} rather than by touching the repository directly. The
     * proxy defers that dependency until first use, by which point both beans
     * exist.
     */
    public YtmCalculationServiceImpl(
            BondCashFlowService bondCashFlowService,
            XirrCalculator xirrCalculator,
            @Lazy BondService bondService) {

        this.bondCashFlowService = bondCashFlowService;
        this.xirrCalculator = xirrCalculator;
        this.bondService = bondService;
    }

    @Override
    @Transactional
    public BigDecimal calculateYtm(Bond bond) {
        // Backward-compatible entry point: use the system date.
        return calculateYtm(bond, LocalDate.now());
    }

    @Override
    @Transactional
    public BigDecimal calculateYtm(Bond bond, LocalDate calculationDate) {

        BigDecimal annualYtmDecimal = annualYtmDecimal(bond, calculationDate);

        // Step 1: Update Bond with the calculated YTM
        BigDecimal annualYtmPercentage = toAnnualYtmPercentage(annualYtmDecimal);

        bond.setAnnualYtm(annualYtmPercentage);
        // Derived from the annual figure, so the two readings cannot disagree.
        bond.setSemiYtm(YtmCalculationService.semiYtmPercentage(annualYtmPercentage));
        bond.setYtmCalculatedAt(Instant.now());

        // Step 2: Persist the Bond
        bondService.saveBond(bond);

        // Step 3: Return the YTM in decimal format (as per service contract)
        return annualYtmDecimal;
    }

    @Override
    public BigDecimal previewAnnualYtm(Bond bond, LocalDate calculationDate) {

        /*
         * Everything the storing path computes, minus the write: the cash flows
         * are generated, the XIRR is solved and the rate is rounded exactly as
         * above, so a yield quoted for an arbitrary date is the same number the
         * platform would have stored had that date been today.
         */
        return toAnnualYtmPercentage(annualYtmDecimal(bond, calculationDate));
    }

    /**
     * The bond's annualised rate as of a date, in decimal form — the one
     * computation both entry points share.
     *
     * <p>
     * Reads nothing the bond stores and writes nothing to it, so it can serve a
     * calculation that must not be persisted as readily as one that must.
     *
     * @return the XIRR as a decimal, e.g. {@code 0.106947}
     * @throws IllegalArgumentException if the bond or the date is null, or the
     *                                  cash flows cannot be built
     * @throws IllegalStateException    if XIRR cannot be solved
     */
    private BigDecimal annualYtmDecimal(Bond bond, LocalDate calculationDate) {

        validateBond(bond);

        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }

        // Step 1: Generate cash flows using BondCashFlowService
        List<XirrCalculator.CashFlow> cashFlows = bondCashFlowService.generateCashFlows(bond, calculationDate);

        // Step 2: Calculate XIRR (returns decimal format like 0.106947)
        // A Sovereign is quoted on a 30/360 year; every other bond keeps the
        // platform's actual/365 basis exactly as before. The basis is shared with
        // PriceCalculationService so the two directions cannot disagree.
        return xirrCalculator.calculate(
                cashFlows,
                BondDayCountBasis.of(bond));
    }

    /**
     * The rate as the platform quotes it: a percentage, to 4 decimals.
     *
     * <p>
     * Example: {@code 0.106947 -> 10.6947}.
     *
     * <p>
     * The rate itself already carries six decimals, so multiplying by 100 lands
     * exactly on four: this step arranges the decimal point rather than
     * discarding anything. Four is where the precision stops being meaningful —
     * a basis point is the fourth place — and the two-decimal figure this used to
     * store was throwing away real differences between bonds that quote to a
     * hundredth of a percent.
     */
    private static BigDecimal toAnnualYtmPercentage(BigDecimal annualYtmDecimal) {

        return annualYtmDecimal
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Validates that the Bond is not null.
     * Delegates cash-flow validation to BondCashFlowService.
     *
     * @param bond the bond to validate
     * @throws IllegalArgumentException if bond is null
     */
    private void validateBond(Bond bond) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
    }
}
