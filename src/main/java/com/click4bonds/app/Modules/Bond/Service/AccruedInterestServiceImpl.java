package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class AccruedInterestServiceImpl implements AccruedInterestService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ACCRUAL_DAYS = BigDecimal.valueOf(365);
    private static final int CALCULATION_SCALE = 20;

    private final CouponScheduleService couponScheduleService;

    public AccruedInterestServiceImpl(CouponScheduleService couponScheduleService) {
        this.couponScheduleService = couponScheduleService;
    }

    @Override
    public BigDecimal calculate(Bond bond, LocalDate calculationDate) {
        validateInput(bond, calculationDate);

        if (bond.getCouponRate().signum() == 0
                || bond.getCouponFrequency() == CouponFrequency.AT_MATURITY) {
            return BigDecimal.ZERO;
        }

        CouponScheduleService.CouponSchedule schedule = couponScheduleService.resolve(bond, calculationDate);
        if (schedule.previous() == null || schedule.next() == null
                || schedule.previous().equals(calculationDate)) {
            return BigDecimal.ZERO;
        }

        BigDecimal frequencyDivisor = frequencyDivisor(bond.getCouponFrequency());
        BigDecimal couponAmount = FACE_VALUE.multiply(bond.getCouponRate())
                .divide(HUNDRED.multiply(frequencyDivisor), CALCULATION_SCALE, RoundingMode.HALF_UP);
        long accruedDays = ChronoUnit.DAYS.between(schedule.previous(), calculationDate);

        return couponAmount.multiply(BigDecimal.valueOf(accruedDays))
                .divide(ACCRUAL_DAYS, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal frequencyDivisor(CouponFrequency frequency) {
        if (frequency == null) {
            throw new IllegalArgumentException("Coupon frequency is required");
        }
        return switch (frequency) {
            case MONTHLY -> BigDecimal.valueOf(12);
            case QUARTERLY -> BigDecimal.valueOf(4);
            case HALF_YEARLY -> BigDecimal.valueOf(2);
            case YEARLY -> BigDecimal.ONE;
            case AT_MATURITY -> throw new IllegalArgumentException(
                    "Unsupported coupon frequency: AT_MATURITY");
        };
    }

    private void validateInput(Bond bond, LocalDate calculationDate) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }
        if (bond.getCouponRate() == null) {
            throw new IllegalArgumentException("Coupon rate is required");
        }
        if (bond.getCouponRate().signum() < 0) {
            throw new IllegalArgumentException("Coupon rate cannot be negative");
        }
        if (bond.getMaturityDate() != null && bond.getMaturityDate().isBefore(calculationDate)) {
            throw new IllegalArgumentException("Bond maturity date is before calculation date");
        }
    }
}