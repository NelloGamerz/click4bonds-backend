// package com.click4bonds.app.Modules.Bond.Service;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.util.ArrayList;
// import java.util.Comparator;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Map;

// import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
// import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
// import com.click4bonds.app.Modules.Bond.Models.Bond;

// @Service
// public class PrincipalRepaymentServiceImpl implements PrincipalRepaymentService {

//     private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
//     private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
//     private static final int CALCULATION_SCALE = 10;

//     private final MaturityDescriptionParser maturityDescriptionParser;

//     public PrincipalRepaymentServiceImpl(MaturityDescriptionParser maturityDescriptionParser) {
//         this.maturityDescriptionParser = maturityDescriptionParser;
//     }

//     @Override
//     public List<PrincipalRepayment> generateRepayments(
//             Bond bond,
//             List<LocalDate> couponDates,
//             LocalDate calculationDate) {
//         validateInput(bond, couponDates, calculationDate);

//         MaturitySchedule schedule = maturityDescriptionParser.parse(
//                 bond.getMaturityDescription(), bond.getMaturityDate());
//         if (schedule.perpetual()) {
//             return List.of();
//         }
//         if (schedule.maturityDate() == null) {
//             throw new IllegalArgumentException("Maturity date is required");
//         }
//         if (schedule.amortizationRules().isEmpty()) {
//             return maturityRepayment(schedule.maturityDate(), calculationDate);
//         }

//         Map<LocalDate, List<AmortizationRule>> rulesByDate = buildScheduledRules(
//                 schedule.amortizationRules(), couponDates, schedule.maturityDate());
//         validateOverlappingRules(rulesByDate);

//         List<PrincipalRepayment> repayments = new ArrayList<>();
//         BigDecimal remainingPrincipal = FACE_VALUE;
//         for (LocalDate date : rulesByDate.keySet().stream().sorted().toList()) {
//             BigDecimal repaymentAmount = scheduledAmount(rulesByDate.get(date))
//                     .min(remainingPrincipal);
//             if (date.equals(schedule.maturityDate())) {
//                 repaymentAmount = remainingPrincipal;
//             }
//             remainingPrincipal = remainingPrincipal.subtract(repaymentAmount);
//             if (date.isAfter(calculationDate) && repaymentAmount.signum() > 0) {
//                 repayments.add(new PrincipalRepayment(date, repaymentAmount, remainingPrincipal));
//             }
//             if (remainingPrincipal.signum() == 0) {
//                 break;
//             }
//         }

//         if (remainingPrincipal.signum() > 0 && schedule.maturityDate().isAfter(calculationDate)) {
//             repayments.add(new PrincipalRepayment(
//                     schedule.maturityDate(), remainingPrincipal, BigDecimal.ZERO));
//         }
//         return consolidateAndSort(repayments);
//     }

//     private Map<LocalDate, List<AmortizationRule>> buildScheduledRules(
//             List<AmortizationRule> rules,
//             List<LocalDate> couponDates,
//             LocalDate maturityDate) {
//         Map<LocalDate, List<AmortizationRule>> rulesByDate = new HashMap<>();
//         for (AmortizationRule rule : rules) {
//             if (rule instanceof IpBasedAmortizationRule) {
//                 for (LocalDate couponDate : new HashSet<>(couponDates)) {
//                     if (isWithin(couponDate, rule.startDate(), rule.endDate())
//                             && !couponDate.isAfter(maturityDate)) {
//                         rulesByDate.computeIfAbsent(couponDate, ignored -> new ArrayList<>()).add(rule);
//                     }
//                 }
//             } else if (rule instanceof FixedFrequencyAmortizationRule fixedRule) {
//                 for (LocalDate date = fixedRule.startDate();
//                         !date.isAfter(fixedRule.endDate()) && !date.isAfter(maturityDate);
//                         date = nextDate(date, fixedRule.frequency())) {
//                     rulesByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(rule);
//                 }
//             }
//         }
//         return rulesByDate;
//     }

//     private void validateOverlappingRules(Map<LocalDate, List<AmortizationRule>> rulesByDate) {
//         for (Map.Entry<LocalDate, List<AmortizationRule>> entry : rulesByDate.entrySet()) {
//             if (entry.getValue().size() > 1) {
//                 throw new IllegalArgumentException(
//                         "Multiple amortization rules apply on " + entry.getKey());
//             }
//         }
//     }

//     private BigDecimal scheduledAmount(List<AmortizationRule> rules) {
//         return FACE_VALUE.multiply(rules.get(0).percentage())
//                 .divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
//     }

//     private LocalDate nextDate(LocalDate date, CouponFrequency frequency) {
//         return switch (frequency) {
//             case MONTHLY -> date.plusMonths(1);
//             case QUARTERLY -> date.plusMonths(3);
//             case HALF_YEARLY -> date.plusMonths(6);
//             case YEARLY -> date.plusYears(1);
//             case AT_MATURITY -> date.plusYears(1);
//         };
//     }

//     private boolean isWithin(LocalDate date, LocalDate startDate, LocalDate endDate) {
//         return !date.isBefore(startDate) && !date.isAfter(endDate);
//     }

//     private List<PrincipalRepayment> maturityRepayment(
//             LocalDate maturityDate, LocalDate calculationDate) {
//         if (!maturityDate.isAfter(calculationDate)) {
//             return List.of();
//         }
//         return List.of(new PrincipalRepayment(maturityDate, FACE_VALUE, BigDecimal.ZERO));
//     }

//     private List<PrincipalRepayment> consolidateAndSort(List<PrincipalRepayment> repayments) {
//         Map<LocalDate, PrincipalRepayment> consolidated = new HashMap<>();
//         for (PrincipalRepayment repayment : repayments) {
//             consolidated.merge(repayment.date(), repayment, (existing, incoming) ->
//                     new PrincipalRepayment(
//                             existing.date(),
//                             existing.principalAmount().add(incoming.principalAmount()),
//                             incoming.remainingPrincipal()));
//         }
//         return consolidated.values().stream()
//                 .sorted(Comparator.comparing(PrincipalRepayment::date))
//                 .toList();
//     }

//     private void validateInput(Bond bond, List<LocalDate> couponDates, LocalDate calculationDate) {
//         if (bond == null) {
//             throw new IllegalArgumentException("Bond cannot be null");
//         }
//         if (couponDates == null) {
//             throw new IllegalArgumentException("Coupon dates cannot be null");
//         }
//         if (calculationDate == null) {
//             throw new IllegalArgumentException("Calculation date cannot be null");
//         }
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class PrincipalRepaymentServiceImpl implements PrincipalRepaymentService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final int CALCULATION_SCALE = 10;

    private final MaturityDescriptionParser maturityDescriptionParser;

    public PrincipalRepaymentServiceImpl(
            MaturityDescriptionParser maturityDescriptionParser) {

        this.maturityDescriptionParser = maturityDescriptionParser;
    }

    @Override
    public List<PrincipalRepayment> generateRepayments(
            Bond bond,
            List<LocalDate> couponDates,
            LocalDate calculationDate) {

        validateInput(
                bond,
                couponDates,
                calculationDate);

        MaturitySchedule schedule = maturityDescriptionParser.parse(
                bond.getMaturityDescription(),
                bond.getMaturityDate());

        /*
         * ------------------------------------------------------------
         * PERPETUAL
         * ------------------------------------------------------------
         */
        if (schedule.perpetual()) {
            return List.of();
        }

        /*
         * ------------------------------------------------------------
         * MATURITY DATE VALIDATION
         * ------------------------------------------------------------
         */
        if (schedule.maturityDate() == null) {
            throw new IllegalArgumentException(
                    "Maturity date is required");
        }

        LocalDate maturityDate = schedule.maturityDate();

        /*
         * ------------------------------------------------------------
         * NORMAL / BULLET BOND
         *
         * No amortization rules means the entire face value is
         * repaid at maturity.
         * ------------------------------------------------------------
         */
        if (schedule.amortizationRules() == null
                || schedule.amortizationRules().isEmpty()) {

            return maturityRepayment(
                    maturityDate,
                    calculationDate);
        }

        /*
         * ------------------------------------------------------------
         * BUILD AMORTIZATION SCHEDULE
         * ------------------------------------------------------------
         *
         * Example:
         *
         * 19/03/2027 to 19/07/2027 (20% Monthly)
         *
         * becomes:
         *
         * 19-Mar-2027 -> 20
         * 19-Apr-2027 -> 20
         * 19-May-2027 -> 20
         * 19-Jun-2027 -> 20
         * 19-Jul-2027 -> 20
         *
         * Total = 100
         * ------------------------------------------------------------
         */
        Map<LocalDate, List<AmortizationRule>> rulesByDate = buildScheduledRules(
                schedule.amortizationRules(),
                couponDates,
                maturityDate);

        validateOverlappingRules(rulesByDate);

        /*
         * ------------------------------------------------------------
         * GENERATE PRINCIPAL REPAYMENTS
         * ------------------------------------------------------------
         */
        List<PrincipalRepayment> repayments = new ArrayList<>();

        BigDecimal remainingPrincipal = FACE_VALUE;

        /*
         * TreeMap guarantees chronological order.
         */
        for (Map.Entry<LocalDate, List<AmortizationRule>> entry : rulesByDate.entrySet()) {

            LocalDate repaymentDate = entry.getKey();

            if (remainingPrincipal.compareTo(ZERO) <= 0) {
                break;
            }

            BigDecimal scheduledAmount = scheduledAmount(entry.getValue());

            /*
             * Never allow scheduled amortization to exceed
             * the remaining principal.
             */
            BigDecimal repaymentAmount = scheduledAmount.min(remainingPrincipal);

            /*
             * At maturity, whatever principal is still outstanding
             * must be repaid.
             *
             * This guarantees the bond is fully redeemed.
             */
            if (repaymentDate.equals(maturityDate)) {
                repaymentAmount = remainingPrincipal;
            }

            if (repaymentAmount.signum() <= 0) {
                continue;
            }

            /*
             * Update principal AFTER determining the repayment.
             */
            remainingPrincipal = remainingPrincipal.subtract(repaymentAmount);

            /*
             * Only future repayments should be returned.
             *
             * However, we still update remainingPrincipal for
             * historical repayments because the current outstanding
             * principal depends on them.
             */
            if (repaymentDate.isAfter(calculationDate)) {

                repayments.add(
                        new PrincipalRepayment(
                                repaymentDate,
                                repaymentAmount,
                                remainingPrincipal));
            }
        }

        /*
         * ------------------------------------------------------------
         * FINAL MATURITY FALLBACK
         * ------------------------------------------------------------
         *
         * If the configured amortization schedule does not completely
         * redeem the face value by maturity, add the remaining
         * principal at maturity.
         *
         * Example:
         *
         * 20% monthly
         * but only four installments are configured:
         *
         * 20 + 20 + 20 + 20 = 80
         *
         * Remaining 20 must still be paid at maturity.
         * ------------------------------------------------------------
         */
        if (remainingPrincipal.signum() > 0
                && maturityDate.isAfter(calculationDate)) {

            repayments.add(
                    new PrincipalRepayment(
                            maturityDate,
                            remainingPrincipal,
                            ZERO));

            remainingPrincipal = ZERO;
        }

        /*
         * ------------------------------------------------------------
         * SAFETY CHECK
         * ------------------------------------------------------------
         */
        if (remainingPrincipal.signum() > 0
                && !maturityDate.isAfter(calculationDate)) {

            /*
             * Maturity has already passed, so there is no future
             * repayment to generate.
             */
            return consolidateAndSort(repayments);
        }

        return consolidateAndSort(repayments);
    }

    /*
     * ============================================================
     * BUILD SCHEDULE
     * ============================================================
     */
    private Map<LocalDate, List<AmortizationRule>> buildScheduledRules(
            List<AmortizationRule> rules,
            List<LocalDate> couponDates,
            LocalDate maturityDate) {

        /*
         * TreeMap is intentional.
         *
         * Principal repayments must always be processed
         * chronologically.
         */
        Map<LocalDate, List<AmortizationRule>> rulesByDate = new TreeMap<>();

        for (AmortizationRule rule : rules) {

            if (rule == null) {
                continue;
            }

            /*
             * --------------------------------------------------------
             * IP-BASED AMORTIZATION
             * --------------------------------------------------------
             *
             * IP-based rules are mapped to coupon dates.
             */
            if (rule instanceof IpBasedAmortizationRule) {

                if (couponDates == null) {
                    continue;
                }

                for (LocalDate couponDate : couponDates) {

                    if (couponDate == null) {
                        continue;
                    }

                    if (couponDate.isAfter(maturityDate)) {
                        continue;
                    }

                    if (isWithin(
                            couponDate,
                            rule.startDate(),
                            rule.endDate())) {

                        rulesByDate
                                .computeIfAbsent(
                                        couponDate,
                                        ignored -> new ArrayList<>())
                                .add(rule);
                    }
                }

                /*
                 * --------------------------------------------------------
                 * FIXED-FREQUENCY AMORTIZATION
                 * --------------------------------------------------------
                 *
                 * Example:
                 *
                 * 19-Mar-2027
                 * 19-Apr-2027
                 * 19-May-2027
                 * ...
                 */
            } else if (rule instanceof FixedFrequencyAmortizationRule fixedRule) {

                validateFixedFrequencyRule(fixedRule);

                LocalDate date = fixedRule.startDate();

                while (!date.isAfter(fixedRule.endDate())
                        && !date.isAfter(maturityDate)) {

                    rulesByDate
                            .computeIfAbsent(
                                    date,
                                    ignored -> new ArrayList<>())
                            .add(fixedRule);

                    LocalDate next = nextDate(
                            date,
                            fixedRule.frequency());

                    /*
                     * Safety check to prevent an accidental infinite
                     * loop if a future frequency produces the same date.
                     */
                    if (!next.isAfter(date)) {
                        throw new IllegalArgumentException(
                                "Amortization frequency did not advance date: "
                                        + date);
                    }

                    date = next;
                }
            }
        }

        return rulesByDate;
    }

    /*
     * ============================================================
     * VALIDATE OVERLAPPING RULES
     * ============================================================
     */
    private void validateOverlappingRules(
            Map<LocalDate, List<AmortizationRule>> rulesByDate) {

        for (Map.Entry<LocalDate, List<AmortizationRule>> entry : rulesByDate.entrySet()) {

            List<AmortizationRule> rules = entry.getValue();

            if (rules == null || rules.isEmpty()) {
                continue;
            }

            if (rules.size() > 1) {

                throw new IllegalArgumentException(
                        "Multiple amortization rules apply on "
                                + entry.getKey());
            }
        }
    }

    /*
     * ============================================================
     * CALCULATE SCHEDULED AMOUNT
     * ============================================================
     *
     * IMPORTANT:
     *
     * Percentage is applied to ORIGINAL FACE VALUE.
     *
     * Example:
     *
     * Face value = 100
     * Percentage = 20%
     *
     * Repayment = 20
     *
     * This is NOT:
     *
     * outstandingPrincipal * 20%
     *
     * ------------------------------------------------------------
     */
    private BigDecimal scheduledAmount(
            List<AmortizationRule> rules) {

        if (rules == null || rules.isEmpty()) {
            return ZERO;
        }

        AmortizationRule rule = rules.get(0);

        BigDecimal percentage = rule.percentage();

        validatePercentage(percentage);

        return FACE_VALUE
                .multiply(percentage)
                .divide(
                        HUNDRED,
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP);
    }

    /*
     * ============================================================
     * NEXT AMORTIZATION DATE
     * ============================================================
     */
    private LocalDate nextDate(
            LocalDate date,
            CouponFrequency frequency) {

        if (date == null) {
            throw new IllegalArgumentException(
                    "Amortization date cannot be null");
        }

        if (frequency == null) {
            throw new IllegalArgumentException(
                    "Amortization frequency cannot be null");
        }

        return switch (frequency) {

            case MONTHLY ->
                date.plusMonths(1);

            case QUARTERLY ->
                date.plusMonths(3);

            case HALF_YEARLY ->
                date.plusMonths(6);

            case YEARLY ->
                date.plusYears(1);

            case AT_MATURITY ->
                date.plusYears(1);
        };
    }

    /*
     * ============================================================
     * DATE RANGE CHECK
     * ============================================================
     */
    private boolean isWithin(
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate) {

        if (date == null
                || startDate == null
                || endDate == null) {

            return false;
        }

        return !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }

    /*
     * ============================================================
     * BULLET / NORMAL MATURITY
     * ============================================================
     */
    private List<PrincipalRepayment> maturityRepayment(
            LocalDate maturityDate,
            LocalDate calculationDate) {

        if (maturityDate == null) {
            throw new IllegalArgumentException(
                    "Maturity date is required");
        }

        if (calculationDate == null) {
            throw new IllegalArgumentException(
                    "Calculation date is required");
        }

        if (!maturityDate.isAfter(calculationDate)) {
            return List.of();
        }

        return List.of(
                new PrincipalRepayment(
                        maturityDate,
                        FACE_VALUE,
                        ZERO));
    }

    /*
     * ============================================================
     * CONSOLIDATE SAME-DATE REPAYMENTS
     * ============================================================
     *
     * Although overlapping rules are currently rejected, this method
     * still safely consolidates same-date entries.
     * ============================================================
     */
    private List<PrincipalRepayment> consolidateAndSort(
            List<PrincipalRepayment> repayments) {

        if (repayments == null || repayments.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, PrincipalRepayment> consolidated = new TreeMap<>();

        for (PrincipalRepayment repayment : repayments) {

            if (repayment == null) {
                continue;
            }

            LocalDate date = repayment.date();

            if (date == null) {
                throw new IllegalArgumentException(
                        "Principal repayment date cannot be null");
            }

            BigDecimal principalAmount = repayment.principalAmount();

            BigDecimal remainingPrincipal = repayment.remainingPrincipal();

            if (principalAmount == null) {
                throw new IllegalArgumentException(
                        "Principal repayment amount cannot be null");
            }

            if (remainingPrincipal == null) {
                throw new IllegalArgumentException(
                        "Remaining principal cannot be null");
            }

            if (principalAmount.signum() < 0) {
                throw new IllegalArgumentException(
                        "Principal repayment cannot be negative");
            }

            consolidated.merge(
                    date,
                    repayment,
                    (existing, incoming) -> {

                        BigDecimal combinedAmount = existing.principalAmount()
                                .add(incoming.principalAmount());

                        return new PrincipalRepayment(
                                date,
                                combinedAmount,
                                incoming.remainingPrincipal());
                    });
        }

        return consolidated.values()
                .stream()
                .sorted(
                        Comparator.comparing(
                                PrincipalRepayment::date))
                .toList();
    }

    /*
     * ============================================================
     * VALIDATE FIXED-FREQUENCY RULE
     * ============================================================
     */
    private void validateFixedFrequencyRule(
            FixedFrequencyAmortizationRule rule) {

        if (rule.startDate() == null) {
            throw new IllegalArgumentException(
                    "Amortization start date is required");
        }

        if (rule.endDate() == null) {
            throw new IllegalArgumentException(
                    "Amortization end date is required");
        }

        if (rule.startDate().isAfter(rule.endDate())) {
            throw new IllegalArgumentException(
                    "Amortization start date cannot be after end date");
        }

        if (rule.frequency() == null) {
            throw new IllegalArgumentException(
                    "Amortization frequency is required");
        }

        validatePercentage(rule.percentage());
    }

    /*
     * ============================================================
     * VALIDATE PERCENTAGE
     * ============================================================
     */
    private void validatePercentage(
            BigDecimal percentage) {

        if (percentage == null) {
            throw new IllegalArgumentException(
                    "Amortization percentage is required");
        }

        if (percentage.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amortization percentage must be greater than zero");
        }

        if (percentage.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "Amortization percentage cannot exceed 100");
        }
    }

    /*
     * ============================================================
     * INPUT VALIDATION
     * ============================================================
     */
    private void validateInput(
            Bond bond,
            List<LocalDate> couponDates,
            LocalDate calculationDate) {

        if (bond == null) {
            throw new IllegalArgumentException(
                    "Bond cannot be null");
        }

        if (couponDates == null) {
            throw new IllegalArgumentException(
                    "Coupon dates cannot be null");
        }

        if (calculationDate == null) {
            throw new IllegalArgumentException(
                    "Calculation date cannot be null");
        }
    }
    
}
