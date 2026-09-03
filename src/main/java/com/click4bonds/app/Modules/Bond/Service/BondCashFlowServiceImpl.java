package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BondCashFlowServiceImpl implements BondCashFlowService {

    private final CouponDateGenerator couponDateGenerator;
    private final PrincipalRepaymentService principalRepaymentService;
    private final CouponCalculationService couponCalculationService;
    private final AccruedInterestService accruedInterestService;

    public BondCashFlowServiceImpl(
            CouponDateGenerator couponDateGenerator,
            PrincipalRepaymentService principalRepaymentService,
            CouponCalculationService couponCalculationService) {
        this(couponDateGenerator, principalRepaymentService, couponCalculationService,
                new AccruedInterestServiceImpl(couponDateGenerator));
    }

    @Override
    public List<XirrCalculator.CashFlow> generateCashFlows(
            Bond bond,
            LocalDate calculationDate) {

        validateInput(bond, calculationDate);

        BigDecimal cleanPrice = bond.getPrice();
        BigDecimal accruedInterest = accruedInterestService.calculate(bond, calculationDate);
        BigDecimal dirtyPrice = cleanPrice.add(accruedInterest);
        Map<LocalDate, BigDecimal> cashFlowsByDate = new TreeMap<>();
        cashFlowsByDate.put(calculationDate, dirtyPrice.negate());

        List<LocalDate> couponDates = safeList(
                couponDateGenerator.generate(bond, calculationDate)
        );
        List<PrincipalRepayment> principalRepayments = safeList(
                principalRepaymentService.generateRepayments(
                        bond,
                        couponDates,
                        calculationDate
                )
        );
        List<CouponPayment> couponPayments = safeList(
                couponCalculationService.calculateCoupons(
                        bond,
                        couponDates,
                        principalRepayments,
                        calculationDate
                )
        );

        mergePositiveCashFlows(cashFlowsByDate, couponPayments, principalRepayments, calculationDate);

        return cashFlowsByDate.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new XirrCalculator.CashFlow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void mergePositiveCashFlows(
            Map<LocalDate, BigDecimal> cashFlowsByDate,
            List<CouponPayment> couponPayments,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {

        for (CouponPayment payment : safeList(couponPayments)) {
            if (payment == null || payment.date() == null || !payment.date().isAfter(calculationDate)) {
                continue;
            }
            cashFlowsByDate.merge(payment.date(), payment.couponAmount(), BigDecimal::add);
        }

        for (PrincipalRepayment repayment : safeList(principalRepayments)) {
            if (repayment == null || repayment.date() == null || !repayment.date().isAfter(calculationDate)) {
                continue;
            }
            cashFlowsByDate.merge(repayment.date(), repayment.principalAmount(), BigDecimal::add);
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void validateInput(Bond bond, LocalDate calculationDate) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }
        if (bond.getPrice() == null) {
            throw new IllegalArgumentException("Bond price is required");
        }
        if (bond.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bond price must be positive");
        }
    }
}
