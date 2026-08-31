package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

public record FixedFrequencyAmortizationRule(BigDecimal percentage, LocalDate startDate, LocalDate endDate,
        CouponFrequency frequency) implements AmortizationRule {
    public FixedFrequencyAmortizationRule {
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
        if (frequency == null) {
            throw new IllegalArgumentException("Amortization frequency is required");
        }
    }
}