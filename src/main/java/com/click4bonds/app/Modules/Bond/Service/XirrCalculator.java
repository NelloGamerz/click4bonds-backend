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

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-10;
    private static final double DERIVATIVE_TOLERANCE = 1e-14;
    private static final double LOWER_RATE = -0.9999999999;
    private static final double INITIAL_UPPER_RATE = 1.0;
    private static final double MAX_RATE = 1e10;

    /**
     * How a span between two dates becomes a fraction of a year.
     *
     * <p>This is the only thing that separates one bond's yield from another's
     * on the same cash flows: the cash flows are discounted by
     * {@code (1 + rate) ^ years}, so the day-count convention decides what
     * {@code years} means. Both halves of the convention live here — how the days
     * are counted, and how many of them make a year — because they are one
     * decision, not two.</p>
     */
    public enum DayCountBasis {

        /**
         * Actual days over a 365-day year — the platform's default, and the
         * basis every corporate bond's yield has been quoted on.
         */
        ACTUAL_365(365.0),

        /**
         * European 30/360 — the basis a Sovereign (government) security is
         * quoted on: every month is thirty days, every year three hundred and
         * sixty, and days 31 are treated as 30.
         *
         * <p>This is the same convention the G-Sec letter's accrued interest is
         * computed on (see {@code GsecAccrualCalculator}), including its choice
         * of the European variant over the US/NASD one, which would also adjust
         * for the end of February. A government security's yield and its accrual
         * are therefore measured in the same year rather than in two different
         * ones.</p>
         */
        THIRTY_360(360.0);

        private static final int DAYS_IN_THIRTY_DAY_MONTH = 30;

        private static final long DAYS_IN_THIRTY_SIXTY_YEAR = 360L;

        private final double daysInYear;

        DayCountBasis(double daysInYear) {
            this.daysInYear = daysInYear;
        }

        /**
         * The span between two dates as a fraction of a year, on this basis.
         *
         * <p>Negative for a date before the start, which the XIRR formula relies
         * on for the initial investment.</p>
         */
        double yearsBetween(LocalDate from, LocalDate to) {

            long days = switch (this) {
                case ACTUAL_365 -> ChronoUnit.DAYS.between(from, to);
                case THIRTY_360 -> thirtyThreeSixtyDaysBetween(from, to);
            };

            return days / daysInYear;
        }

        /**
         * The European 30/360 day count: days 31 are treated as 30, and every
         * month and year then has its full 30 and 360 days.
         */
        private static long thirtyThreeSixtyDaysBetween(LocalDate from, LocalDate to) {

            int fromDay = Math.min(from.getDayOfMonth(), DAYS_IN_THIRTY_DAY_MONTH);
            int toDay = Math.min(to.getDayOfMonth(), DAYS_IN_THIRTY_DAY_MONTH);

            return (to.getYear() - from.getYear()) * DAYS_IN_THIRTY_SIXTY_YEAR
                    + (to.getMonthValue() - from.getMonthValue()) * DAYS_IN_THIRTY_DAY_MONTH
                    + (toDay - fromDay);
        }
    }

    /**
     * XIRR on the platform's default actual/365 basis.
     *
     * @param cashFlows the dated cash flows, one of which must be negative
     * @return the annualised rate as a decimal, e.g. {@code 0.106947}
     */
    public BigDecimal calculate(List<CashFlow> cashFlows) {
        return calculate(cashFlows, DayCountBasis.ACTUAL_365);
    }

    /**
     * XIRR on a caller-chosen day-count basis.
     *
     * @param cashFlows the dated cash flows, one of which must be negative
     * @param basis     {@link DayCountBasis#ACTUAL_365} for a corporate bond,
     *                  {@link DayCountBasis#THIRTY_360} for a Sovereign
     * @return the annualised rate as a decimal, e.g. {@code 0.106947}
     */
    public BigDecimal calculate(List<CashFlow> cashFlows, DayCountBasis basis) {
        validateCashFlows(cashFlows);

        if (basis == null) {
            throw new IllegalArgumentException("Day-count basis cannot be null");
        }

        List<CashFlow> sortedCashFlows = new ArrayList<>(cashFlows);
        sortedCashFlows.sort((first, second) -> first.date().compareTo(second.date()));
        LocalDate startDate = sortedCashFlows.get(0).date();

        double lowerRate = LOWER_RATE;
        double upperRate = INITIAL_UPPER_RATE;
        double lowerNpv = npv(sortedCashFlows, startDate, lowerRate, basis);
        double upperNpv = npv(sortedCashFlows, startDate, upperRate, basis);

        while (sameSign(lowerNpv, upperNpv) && upperRate < MAX_RATE) {
            upperRate = Math.min(MAX_RATE, 2.0 * (upperRate + 1.0) - 1.0);
            upperNpv = npv(sortedCashFlows, startDate, upperRate, basis);
        }

        if (!hasSignChange(lowerNpv, upperNpv)) {
            throw new IllegalStateException("Unable to bracket an XIRR solution for the given cash flows");
        }

        double rate = 0.08;
        if (rate <= lowerRate || rate >= upperRate) {
            rate = midpoint(lowerRate, upperRate);
        }

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double currentNpv = npv(sortedCashFlows, startDate, rate, basis);

            if (Math.abs(currentNpv) < TOLERANCE) {
                return BigDecimal.valueOf(rate).setScale(6, RoundingMode.HALF_UP);
            }

            if (sameSign(lowerNpv, currentNpv)) {
                lowerRate = rate;
                lowerNpv = currentNpv;
            } else {
                upperRate = rate;
            }

            double derivative = derivative(sortedCashFlows, startDate, rate, basis);
            double newRate = rate;
            if (Double.isFinite(derivative) && Math.abs(derivative) >= DERIVATIVE_TOLERANCE) {
                newRate = rate - (currentNpv / derivative);
            }

            if (!Double.isFinite(newRate) || newRate <= lowerRate || newRate >= upperRate) {
                newRate = midpoint(lowerRate, upperRate);
            }

            if (Math.abs(upperRate - lowerRate) < TOLERANCE * Math.max(1.0, Math.abs(rate))) {
                double solution = midpoint(lowerRate, upperRate);
                if (Math.abs(npv(sortedCashFlows, startDate, solution, basis)) < 1e-7) {
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

    private double npv(
            List<CashFlow> cashFlows,
            LocalDate startDate,
            double rate,
            DayCountBasis basis) {
        double value = 0.0;
        for (CashFlow cashFlow : cashFlows) {
            double years = basis.yearsBetween(startDate, cashFlow.date());
            value += cashFlow.amount().doubleValue() / Math.pow(1.0 + rate, years);
        }
        return value;
    }

    private double derivative(
            List<CashFlow> cashFlows,
            LocalDate startDate,
            double rate,
            DayCountBasis basis) {
        double value = 0.0;
        for (CashFlow cashFlow : cashFlows) {
            double years = basis.yearsBetween(startDate, cashFlow.date());
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
