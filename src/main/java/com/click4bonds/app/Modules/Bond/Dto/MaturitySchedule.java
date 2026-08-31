package com.click4bonds.app.Modules.Bond.Dto;

import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Service.AmortizationRule;

public record MaturitySchedule(LocalDate maturityDate, List<AmortizationRule> amortizationRules, boolean perpetual) {
    public MaturitySchedule {
        if (maturityDate == null && !perpetual) {
            throw new IllegalArgumentException("Maturity date is required for non-perpetual bonds");
        }
        if (amortizationRules == null) {
            throw new IllegalArgumentException("Amortization rules cannot be null");
        }
        amortizationRules = List.copyOf(amortizationRules);
    }

    public boolean isAmortizing() {
        return !amortizationRules.isEmpty();
    }
}