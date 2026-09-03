package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponPayment(
        LocalDate date,
        BigDecimal couponAmount,
        BigDecimal outstandingPrincipalBeforePayment
) {
}