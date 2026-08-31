package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IpBasedAmortizationRule(BigDecimal percentage, LocalDate startDate, LocalDate endDate)
        implements AmortizationRule {
    public IpBasedAmortizationRule {
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Amortization percentage must be between 0 and 100");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Amortization start date is required");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("Amortization end date is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Amortization end date cannot be before start date");
        }
    }
}
