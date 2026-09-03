package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class CouponCalculationServiceImpl implements CouponCalculationService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int CALCULATION_SCALE = 10;

    @Override
    public List<CouponPayment> calculateCoupons(
            Bond bond,
            List<LocalDate> couponDates,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {
        validateInput(bond, couponDates, principalRepayments, calculationDate);

        BigDecimal couponRate = bond.getCouponRate();
        if (couponRate == null) {
            throw new IllegalArgumentException("Coupon rate is required");
        }
        if (couponRate.signum() < 0) {
            throw new IllegalArgumentException("Coupon rate cannot be negative");
        }

        BigDecimal frequencyDivisor = frequencyDivisor(bond.getCouponFrequency());
        Map<LocalDate, BigDecimal> repaymentsByDate = repaymentsByDate(principalRepayments);
        BigDecimal outstandingPrincipal = initialOutstandingPrincipal(principalRepayments);
        List<LocalDate> paymentDates = couponDates.stream()
                .filter(date -> date.isAfter(calculationDate))
                .filter(date -> bond.getMaturityDate() == null || !date.isAfter(bond.getMaturityDate()))
                .distinct()
                .sorted()
                .toList();

        List<CouponPayment> payments = new java.util.ArrayList<>();
        int repaymentIndex = 0;
        List<LocalDate> repaymentDates = repaymentsByDate.keySet().stream().sorted().toList();
        for (LocalDate paymentDate : paymentDates) {
            while (repaymentIndex < repaymentDates.size()
                    && repaymentDates.get(repaymentIndex).isBefore(paymentDate)) {
                outstandingPrincipal = outstandingPrincipal.subtract(
                        repaymentsByDate.get(repaymentDates.get(repaymentIndex)));
                repaymentIndex++;
            }

            BigDecimal couponAmount = outstandingPrincipal
                    .multiply(couponRate)
                    .divide(HUNDRED.multiply(frequencyDivisor), CALCULATION_SCALE, RoundingMode.HALF_UP);
            payments.add(new CouponPayment(paymentDate, couponAmount, outstandingPrincipal));

            if (repaymentIndex < repaymentDates.size()
                    && repaymentDates.get(repaymentIndex).equals(paymentDate)) {
                outstandingPrincipal = outstandingPrincipal.subtract(repaymentsByDate.get(paymentDate));
                repaymentIndex++;
            }
        }
        return payments;
    }

    private Map<LocalDate, BigDecimal> repaymentsByDate(List<PrincipalRepayment> repayments) {
        Map<LocalDate, BigDecimal> repaymentsByDate = new LinkedHashMap<>();
        for (PrincipalRepayment repayment : repayments) {
            if (repayment == null || repayment.date() == null
                    || repayment.principalAmount() == null || repayment.remainingPrincipal() == null) {
                throw new IllegalArgumentException("Principal repayments must contain complete values");
            }
            if (repayment.principalAmount().signum() < 0) {
                throw new IllegalArgumentException("Principal repayment cannot be negative");
            }
                repaymentsByDate.put(repayment.date(), repaymentsByDate
                    .getOrDefault(repayment.date(), BigDecimal.ZERO)
                    .add(repayment.principalAmount()));
        }
        return repaymentsByDate;
    }

    private BigDecimal initialOutstandingPrincipal(List<PrincipalRepayment> repayments) {
        if (repayments.isEmpty()) {
            return FACE_VALUE;
        }
        PrincipalRepayment firstRepayment = repayments.stream()
            .min(Comparator.comparing(repayment -> repayment.date()))
                .orElseThrow();
        return firstRepayment.remainingPrincipal().add(firstRepayment.principalAmount());
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

    private void validateInput(
            Bond bond,
            List<LocalDate> couponDates,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (couponDates == null) {
            throw new IllegalArgumentException("Coupon dates cannot be null");
        }
        if (principalRepayments == null) {
            throw new IllegalArgumentException("Principal repayments cannot be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }
        if (couponDates.stream().anyMatch(date -> date == null)) {
            throw new IllegalArgumentException("Coupon dates cannot contain null");
        }
    }
}