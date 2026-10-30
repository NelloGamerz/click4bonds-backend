package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
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

    /**
     * The annual YTM a bond yields when bought at {@code pricePercentage}, as of
     * a date, calculated but not stored.
     *
     * <p>
     * The same cash flows and the same day count as {@link #previewAnnualYtm},
     * except that the purchase leg is priced at the caller's figure rather than
     * the bond's stored price. This is what lets a deal be priced — a negotiated
     * price, or one quoted for that one client — without restating the bond's own
     * market price or yield.
     *
     * <p>
     * Nothing is written to the bond, which is why an {@code annualYtm} set from
     * this method is the caller's to keep (see {@code DealConfirmation}).
     *
     * @param bond            the bond to calculate YTM for
     * @param calculationDate the settlement/calculation date to use
     * @param pricePercentage the clean price to buy at, as a percentage of par,
     *                        e.g. {@code 98.94}
     * @return the annual YTM as a percentage to 4 decimals
     * @throws IllegalArgumentException if an argument is {@code null}
     * @throws IllegalStateException    if XIRR cannot be solved
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ConflictException
     *         when the bond has no future cash flow to yield
     */
    BigDecimal previewAnnualYtm(Bond bond, LocalDate calculationDate, BigDecimal pricePercentage);

    /**
     * The semi-annual equivalent of an annual yield: the rate that, compounded
     * twice a year, produces the same annual growth.
     *
     * <p>{@code semi = 2 × (√(1 + annual) − 1)}, so 7.20% annual is 7.0749%
     * semi-annual. Both inputs and the result are percentages, to 4 decimals.
     *
     * @param annualYtmPercentage the annual YTM as a percentage, e.g. 7.20
     * @return the semi-annual YTM as a percentage, or {@code null} when the
     *         annual yield is {@code null}
     */
    static BigDecimal semiYtmPercentage(BigDecimal annualYtmPercentage) {

        if (annualYtmPercentage == null) {
            return null;
        }

        return BigDecimal.ONE
                .add(annualYtmPercentage.movePointLeft(2))
                .sqrt(new MathContext(15))
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(200))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
