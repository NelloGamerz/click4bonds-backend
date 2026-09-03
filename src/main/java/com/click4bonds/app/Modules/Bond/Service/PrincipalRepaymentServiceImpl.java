package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class PrincipalRepaymentServiceImpl implements PrincipalRepaymentService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int CALCULATION_SCALE = 10;

    private final MaturityDescriptionParser maturityDescriptionParser;

    public PrincipalRepaymentServiceImpl(MaturityDescriptionParser maturityDescriptionParser) {
        this.maturityDescriptionParser = maturityDescriptionParser;
    }

    @Override
    public List<PrincipalRepayment> generateRepayments(
            Bond bond,
            List<LocalDate> couponDates,
            LocalDate calculationDate) {
        validateInput(bond, couponDates, calculationDate);

        MaturitySchedule schedule = maturityDescriptionParser.parse(
                bond.getMaturityDescription(), bond.getMaturityDate());
        if (schedule.perpetual()) {
            return List.of();
        }
        if (schedule.maturityDate() == null) {
            throw new IllegalArgumentException("Maturity date is required");
        }
        if (schedule.amortizationRules().isEmpty()) {
            return maturityRepayment(schedule.maturityDate(), calculationDate);
        }

        Map<LocalDate, List<AmortizationRule>> rulesByDate = buildScheduledRules(
                schedule.amortizationRules(), couponDates, schedule.maturityDate());
        validateOverlappingRules(rulesByDate);

        List<PrincipalRepayment> repayments = new ArrayList<>();
        BigDecimal remainingPrincipal = FACE_VALUE;
        for (LocalDate date : rulesByDate.keySet().stream().sorted().toList()) {
            BigDecimal repaymentAmount = scheduledAmount(rulesByDate.get(date))
                    .min(remainingPrincipal);
            if (date.equals(schedule.maturityDate())) {
                repaymentAmount = remainingPrincipal;
            }
            remainingPrincipal = remainingPrincipal.subtract(repaymentAmount);
            if (date.isAfter(calculationDate) && repaymentAmount.signum() > 0) {
                repayments.add(new PrincipalRepayment(date, repaymentAmount, remainingPrincipal));
            }
            if (remainingPrincipal.signum() == 0) {
                break;
            }
        }

        if (remainingPrincipal.signum() > 0 && schedule.maturityDate().isAfter(calculationDate)) {
            repayments.add(new PrincipalRepayment(
                    schedule.maturityDate(), remainingPrincipal, BigDecimal.ZERO));
        }
        return consolidateAndSort(repayments);
    }

    private Map<LocalDate, List<AmortizationRule>> buildScheduledRules(
            List<AmortizationRule> rules,
            List<LocalDate> couponDates,
            LocalDate maturityDate) {
        Map<LocalDate, List<AmortizationRule>> rulesByDate = new HashMap<>();
        for (AmortizationRule rule : rules) {
            if (rule instanceof IpBasedAmortizationRule) {
                for (LocalDate couponDate : new HashSet<>(couponDates)) {
                    if (isWithin(couponDate, rule.startDate(), rule.endDate())
                            && !couponDate.isAfter(maturityDate)) {
                        rulesByDate.computeIfAbsent(couponDate, ignored -> new ArrayList<>()).add(rule);
                    }
                }
            } else if (rule instanceof FixedFrequencyAmortizationRule fixedRule) {
                for (LocalDate date = fixedRule.startDate();
                        !date.isAfter(fixedRule.endDate()) && !date.isAfter(maturityDate);
                        date = nextDate(date, fixedRule.frequency())) {
                    rulesByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(rule);
                }
            }
        }
        return rulesByDate;
    }

    private void validateOverlappingRules(Map<LocalDate, List<AmortizationRule>> rulesByDate) {
        for (Map.Entry<LocalDate, List<AmortizationRule>> entry : rulesByDate.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple amortization rules apply on " + entry.getKey());
            }
        }
    }

    private BigDecimal scheduledAmount(List<AmortizationRule> rules) {
        return FACE_VALUE.multiply(rules.get(0).percentage())
                .divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate nextDate(LocalDate date, CouponFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case HALF_YEARLY -> date.plusMonths(6);
            case YEARLY -> date.plusYears(1);
            case AT_MATURITY -> date.plusYears(1);
        };
    }

    private boolean isWithin(LocalDate date, LocalDate startDate, LocalDate endDate) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private List<PrincipalRepayment> maturityRepayment(
            LocalDate maturityDate, LocalDate calculationDate) {
        if (!maturityDate.isAfter(calculationDate)) {
            return List.of();
        }
        return List.of(new PrincipalRepayment(maturityDate, FACE_VALUE, BigDecimal.ZERO));
    }

    private List<PrincipalRepayment> consolidateAndSort(List<PrincipalRepayment> repayments) {
        Map<LocalDate, PrincipalRepayment> consolidated = new HashMap<>();
        for (PrincipalRepayment repayment : repayments) {
            consolidated.merge(repayment.date(), repayment, (existing, incoming) ->
                    new PrincipalRepayment(
                            existing.date(),
                            existing.principalAmount().add(incoming.principalAmount()),
                            incoming.remainingPrincipal()));
        }
        return consolidated.values().stream()
                .sorted(Comparator.comparing(PrincipalRepayment::date))
                .toList();
    }

    private void validateInput(Bond bond, List<LocalDate> couponDates, LocalDate calculationDate) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (couponDates == null) {
            throw new IllegalArgumentException("Coupon dates cannot be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }
    }
}