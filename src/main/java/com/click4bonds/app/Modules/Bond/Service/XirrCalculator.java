package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class XirrCalculator {

    private static final double DAYS_IN_YEAR = 365.0;
    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-10;

    /**
     * Calculates annualized XIRR for irregularly dated cash flows.
     *
     * Example:
     *
     * 23-Aug-2026 -97.50
     * 09-Feb-2027 +4.395
     * 09-Aug-2027 +104.395
     *
     * Returns annualized yield.
     */
    public BigDecimal calculate(List<CashFlow> cashFlows) {

        validateCashFlows(cashFlows);

        double rate = 0.08; // Initial guess = 8%

        LocalDate startDate = cashFlows.get(0).date();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {

            double npv = 0.0;
            double derivative = 0.0;

            for (CashFlow cashFlow : cashFlows) {

                double years = ChronoUnit.DAYS.between(
                        startDate,
                        cashFlow.date()) / DAYS_IN_YEAR;

                double amount = cashFlow.amount().doubleValue();

                double denominator = Math.pow(1.0 + rate, years);

                npv += amount / denominator;

                derivative += (-years * amount)
                        / Math.pow(
                                1.0 + rate,
                                years + 1);
            }

            // Converged
            if (Math.abs(npv) < TOLERANCE) {
                return BigDecimal.valueOf(rate)
                        .setScale(6, RoundingMode.HALF_UP);
            }

            // Cannot continue Newton-Raphson
            if (Math.abs(derivative) < 1e-14) {
                break;
            }

            double newRate = rate - (npv / derivative);

            // Invalid result
            if (Double.isNaN(newRate)
                    || Double.isInfinite(newRate)
                    || newRate <= -1.0) {

                break;
            }

            // Rate has converged
            if (Math.abs(newRate - rate) < TOLERANCE) {

                return BigDecimal.valueOf(newRate)
                        .setScale(6, RoundingMode.HALF_UP);
            }

            rate = newRate;
        }

        throw new IllegalStateException(
                "Unable to calculate XIRR for the given cash flows");
    }

    private void validateCashFlows(
            List<CashFlow> cashFlows) {

        if (cashFlows == null || cashFlows.size() < 2) {
            throw new IllegalArgumentException(
                    "At least two cash flows are required");
        }

        boolean hasNegative = cashFlows.stream()
                .anyMatch(cf -> cf.amount().signum() < 0);

        boolean hasPositive = cashFlows.stream()
                .anyMatch(cf -> cf.amount().signum() > 0);

        if (!hasNegative || !hasPositive) {
            throw new IllegalArgumentException(
                    "Cash flows must contain both " +
                            "positive and negative amounts");
        }
    }

    public record 
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    CashFlow(
            LocalDate date,
            BigDecimal amount) {
    }
}
