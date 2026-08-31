package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrincipalRepayment(
        LocalDate date,
        BigDecimal principalAmount,
        BigDecimal remainingPrincipal
) {
}
