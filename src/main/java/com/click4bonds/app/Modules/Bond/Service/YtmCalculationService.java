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

    /**
     * The annual YTM as of an arbitrary date, calculated but not stored.
     *
     * <p>
     * The same cash flows, the same XIRR and the same rounding as
     * {@link #calculateYtm(Bond, LocalDate)} — the only difference is what it
     * leaves behind: nothing. The bond is not modified, {@code ytmCalculatedAt}
     * is not touched and no save is issued, so a caller asking what a bond would
     * have yielded on some past or future date cannot overwrite the figure the
     * platform quotes today.
     *
     * <p>
     * It exists for that question. A yield belongs to a date, and only the
     * current date's is worth keeping: the stored {@code annualYtm} is the
     * platform's answer today, and a hypothetical date is an answer to a
     * one-off question.
     *
     * @param bond            the bond to calculate YTM for
     * @param calculationDate the settlement/calculation date to use
     * @return the annual YTM as a percentage to 4 decimals, exactly as
     *         {@link #calculateYtm(Bond, LocalDate)} would have stored it
     * @throws IllegalArgumentException if bond is null or missing required fields
     * @throws IllegalStateException    if XIRR calculation fails
     */
    BigDecimal previewAnnualYtm(Bond bond, LocalDate calculationDate);
}
