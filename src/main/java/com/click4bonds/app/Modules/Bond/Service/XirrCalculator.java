package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class XirrCalculator {

    private static final double DAYS_IN_YEAR = 365.0;
    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-10;
    private static final double DERIVATIVE_TOLERANCE = 1e-14;
    private static final double LOWER_RATE = -0.9999999999;
    private static final double INITIAL_UPPER_RATE = 1.0;
    private static final double MAX_RATE = 1e10;

    public BigDecimal calculate(List<CashFlow> cashFlows) {
        validateCashFlows(cashFlows);

        List<CashFlow> sortedCashFlows = new ArrayList<>(cashFlows);
        sortedCashFlows.sort((first, second) -> first.date().compareTo(second.date()));
        LocalDate startDate = sortedCashFlows.get(0).date();

        double lowerRate = LOWER_RATE;
        double upperRate = INITIAL_UPPER_RATE;
        double lowerNpv = npv(sortedCashFlows, startDate, lowerRate);
        double upperNpv = npv(sortedCashFlows, startDate, upperRate);

        while (sameSign(lowerNpv, upperNpv) && upperRate < MAX_RATE) {
            upperRate = Math.min(MAX_RATE, 2.0 * (upperRate + 1.0) - 1.0);
            upperNpv = npv(sortedCashFlows, startDate, upperRate);
        }

        if (!hasSignChange(lowerNpv, upperNpv)) {
            throw new IllegalStateException("Unable to bracket an XIRR solution for the given cash flows");
        }

        double rate = 0.08;
        if (rate <= lowerRate || rate >= upperRate) {
            rate = midpoint(lowerRate, upperRate);
        }

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double currentNpv = npv(sortedCashFlows, startDate, rate);

            if (Math.abs(currentNpv) < TOLERANCE) {
                return BigDecimal.valueOf(rate).setScale(6, RoundingMode.HALF_UP);
            }

            if (sameSign(lowerNpv, currentNpv)) {
                lowerRate = rate;
                lowerNpv = currentNpv;
            } else {
                upperRate = rate;
            }

            double derivative = derivative(sortedCashFlows, startDate, rate);
            double newRate = rate;
            if (Double.isFinite(derivative) && Math.abs(derivative) >= DERIVATIVE_TOLERANCE) {
                newRate = rate - (currentNpv / derivative);
            }

            if (!Double.isFinite(newRate) || newRate <= lowerRate || newRate >= upperRate) {
                newRate = midpoint(lowerRate, upperRate);
            }

            if (Math.abs(upperRate - lowerRate) < TOLERANCE * Math.max(1.0, Math.abs(rate))) {
                double solution = midpoint(lowerRate, upperRate);
                if (Math.abs(npv(sortedCashFlows, startDate, solution)) < 1e-7) {
                    return BigDecimal.valueOf(solution).setScale(6, RoundingMode.HALF_UP);
                }
            }
            rate = newRate;
        }

        throw new IllegalStateException("Unable to calculate XIRR for the given cash flows");
    }

    private void validateCashFlows(List<CashFlow> cashFlows) {
        if (cashFlows == null) {
            throw new IllegalArgumentException("Cash flows cannot be null");
        }
        if (cashFlows.size() < 2) {
            throw new IllegalArgumentException("At least two cash flows are required");
        }

        boolean hasNegative = false;
        boolean hasPositive = false;
        for (CashFlow cashFlow : cashFlows) {
            if (cashFlow == null) {
                throw new IllegalArgumentException("Cash-flow entries cannot be null");
            }
            if (cashFlow.date() == null) {
                throw new IllegalArgumentException("Cash-flow dates cannot be null");
            }
            if (cashFlow.amount() == null) {
                throw new IllegalArgumentException("Cash-flow amounts cannot be null");
            }
            if (cashFlow.amount().signum() == 0) {
                throw new IllegalArgumentException("Cash-flow amounts cannot be zero");
            }
            hasNegative |= cashFlow.amount().signum() < 0;
            hasPositive |= cashFlow.amount().signum() > 0;
        }

        if (!hasNegative || !hasPositive) {
            throw new IllegalArgumentException("Cash flows must contain both positive and negative amounts");
        }
    }

    private double npv(List<CashFlow> cashFlows, LocalDate startDate, double rate) {
        double value = 0.0;
        for (CashFlow cashFlow : cashFlows) {
            double years = ChronoUnit.DAYS.between(startDate, cashFlow.date()) / DAYS_IN_YEAR;
            value += cashFlow.amount().doubleValue() / Math.pow(1.0 + rate, years);
        }
        return value;
    }

    private double derivative(List<CashFlow> cashFlows, LocalDate startDate, double rate) {
        double value = 0.0;
        for (CashFlow cashFlow : cashFlows) {
            double years = ChronoUnit.DAYS.between(startDate, cashFlow.date()) / DAYS_IN_YEAR;
            value += (-years * cashFlow.amount().doubleValue())
                    / Math.pow(1.0 + rate, years + 1.0);
        }
        return value;
    }

    private boolean sameSign(double first, double second) {
        return Math.signum(first) == Math.signum(second);
    }

    private boolean hasSignChange(double first, double second) {
        return Double.isFinite(first) && Double.isFinite(second) && !sameSign(first, second);
    }

    private double midpoint(double lower, double upper) {
        return lower + (upper - lower) / 2.0;
    }

    public record CashFlow(
            LocalDate date,
            BigDecimal amount) {
    }
}
