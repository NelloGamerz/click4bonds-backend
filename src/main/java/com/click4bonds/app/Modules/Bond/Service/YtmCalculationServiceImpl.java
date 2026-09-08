package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YtmCalculationServiceImpl implements YtmCalculationService {

    private final BondCashFlowService bondCashFlowService;
    private final XirrCalculator xirrCalculator;
    private final BondRepository bondRepository;

    @Override
    @Transactional
    public BigDecimal calculateYtm(Bond bond) {
        // Backward-compatible entry point: use the system date.
        return calculateYtm(bond, LocalDate.now());
    }

    @Override
    @Transactional
    public BigDecimal calculateYtm(Bond bond, LocalDate calculationDate) {
        validateBond(bond);

        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }

        // Step 1: Generate cash flows using BondCashFlowService
        List<XirrCalculator.CashFlow> cashFlows = bondCashFlowService.generateCashFlows(bond, calculationDate);

        // Step 2: Calculate XIRR (returns decimal format like 0.106947)
        BigDecimal annualYtmDecimal = xirrCalculator.calculate(cashFlows);

        // Step 3: Convert from decimal to percentage and round to 2 decimal places
        // Example: 0.106947 -> 10.6947 -> 10.69
        BigDecimal annualYtmPercentage = annualYtmDecimal
        .multiply(new BigDecimal("100"))
        .setScale(2, RoundingMode.HALF_UP);

        // BigDecimal annualYtmPercentage = annualYtmDecimal
        //         .multiply(new BigDecimal("100"))
        //         .divide(new BigDecimal("0.10"), 0, RoundingMode.HALF_UP)
        //         .multiply(new BigDecimal("0.10"))
        //         .setScale(2, RoundingMode.HALF_UP);

        // Step 4: Update Bond with calculated YTM
        bond.setAnnualYtm(annualYtmPercentage);
        bond.setYtmCalculatedAt(Instant.now());

        // Step 5: Persist the Bond
        bondRepository.save(bond);

        // Step 6: Return the YTM in decimal format (as per service contract)
        return annualYtmDecimal;
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
