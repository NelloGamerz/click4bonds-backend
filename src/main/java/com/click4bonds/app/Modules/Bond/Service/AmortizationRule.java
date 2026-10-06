package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

public sealed interface AmortizationRule
        permits FixedFrequencyAmortizationRule, IpBasedAmortizationRule, DatedAmortizationRule {
    BigDecimal percentage();

    LocalDate startDate();

    LocalDate endDate();
}
