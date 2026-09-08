package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface YtmCalculationService {

    /**
     * Calculates the annual Yield to Maturity (YTM) for a bond using the
     * system date (LocalDate.now()) as the calculation date.
     *
     * Calculation flow:
     * 1. Validate Bond
     * 2. calculationDate = LocalDate.now()
     * 3. BondCashFlowService.generateCashFlows(bond, calculationDate)
     * 4. XirrCalculator.calculate(cashFlows)
     * 5. Persist annual YTM on Bond
     * 6. Persist ytmCalculatedAt
     * 7. Return calculated YTM
     *
     * @param bond the bond to calculate YTM for
     * @return the calculated annual YTM as a decimal (e.g., 0.106947 for 10.6947%)
     * @throws IllegalArgumentException if bond is null or missing required fields
     * @throws IllegalStateException if XIRR calculation fails
     */
    BigDecimal calculateYtm(Bond bond);

    /**
     * Calculates the annual Yield to Maturity (YTM) for a bond using an
     * explicit, fixed calculation date. This overload is deterministic:
     * the supplied date is used for the entire calculation and the system
     * clock is never read.
     *
     * Calculation flow:
     * 1. Validate Bond
     * 2. calculationDate = the supplied date
     * 3. BondCashFlowService.generateCashFlows(bond, calculationDate)
     * 4. XirrCalculator.calculate(cashFlows)
     * 5. Persist annual YTM on Bond
     * 6. Persist ytmCalculatedAt
     * 7. Return calculated YTM
     *
     * @param bond the bond to calculate YTM for
     * @param calculationDate the fixed settlement/calculation date to use
     * @return the calculated annual YTM as a decimal (e.g., 0.106947 for 10.6947%)
     * @throws IllegalArgumentException if bond is null or missing required fields
     * @throws IllegalStateException if XIRR calculation fails
     */
    BigDecimal calculateYtm(Bond bond, LocalDate calculationDate);
}
