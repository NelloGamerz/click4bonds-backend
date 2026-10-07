package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Dto.AccruedInterest;
import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface AccruedInterestService {

    /**
     * Accrued days and the interest they earn, as one computation.
     *
     * @param bond            the bond
     * @param calculationDate the settlement date the accrual is measured to
     * @return the accrued days and their interest; never null
     */
    AccruedInterest accrue(Bond bond, LocalDate calculationDate);

    /**
     * The accrued interest alone.
     *
     * <p>
     * Derived from {@link #accrue} rather than computed again, so a caller that
     * wants only the money and a caller that wants the day count too can never
     * be shown two different answers.
     */
    default BigDecimal calculate(Bond bond, LocalDate calculationDate) {
        return accrue(bond, calculationDate).amount();
    }
}
