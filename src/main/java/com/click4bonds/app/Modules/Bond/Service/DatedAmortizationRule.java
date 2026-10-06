package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A principal repayment on one explicitly stated date, repaying the given
 * percentage of face value.
 *
 * <p>
 * The other rules describe a shape — a frequency walked between two dates, or a
 * window of coupon dates — and the actual dates are derived. This one is
 * already a date: a bond whose sheet spells out its redemption schedule, e.g.
 *
 * <pre>
 * 27/09/2027(30%), 27/10/2027(30%), 27/11/2027(40%)
 * </pre>
 *
 * <p>
 * One such bond produces one rule per entry. {@link #startDate()} and
 * {@link #endDate()} are both the repayment date, because the rule covers a
 * single day; callers that need the date should read {@link #repaymentDate()}.
 *
 * @param percentage    share of face value repaid, greater than 0 and at most 100
 * @param repaymentDate the date the repayment falls on
 */
public record DatedAmortizationRule(BigDecimal percentage, LocalDate repaymentDate)
        implements AmortizationRule {

    public DatedAmortizationRule {

        if (percentage == null || percentage.signum() <= 0
                || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {

            throw new IllegalArgumentException(
                    "Amortization percentage must be between 0 and 100");
        }

        if (repaymentDate == null) {
            throw new IllegalArgumentException(
                    "Amortization repayment date is required");
        }
    }

    @Override
    public LocalDate startDate() {
        return repaymentDate;
    }

    @Override
    public LocalDate endDate() {
        return repaymentDate;
    }
}
