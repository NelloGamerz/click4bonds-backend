package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Comprehensive audit test for YTM calculation on staged IP amortized bonds.
 * 
 * This test prints detailed information about coupon dates, principal repayments,
 * cash flows, and XIRR calculation to diagnose YTM discrepancies.
 */
@ExtendWith(MockitoExtension.class)
class YtmAuditTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    @Test
    void auditStagedAmortizationBondCashFlows() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("YTM AUDIT: STAGED AMORTIZATION BOND");
        System.out.println("=".repeat(100));

        Bond bond = createStagedBond();
        
        System.out.println("\n[BOND CONFIGURATION]");
        System.out.println("Calculation Date : " + CALCULATION_DATE);
        System.out.println("Clean Price      : " + bond.getPrice());
        System.out.println("Coupon Rate      : " + bond.getCouponRate() + "%");
        System.out.println("Frequency        : " + bond.getCouponFrequency());
        System.out.println("IP Description   : " + bond.getIpDateDescription());
        System.out.println("Maturity Date    : " + bond.getMaturityDate());
        System.out.println("Maturity Descr   : " + bond.getMaturityDescription());

        // Create services
        CouponDateGenerator couponDateGenerator = new CouponDateGenerator();
        PrincipalRepaymentService principalService = new PrincipalRepaymentServiceImpl(
                new MaturityDescriptionParserImpl());
        CouponCalculationService couponService = new CouponCalculationServiceImpl();
        AccruedInterestService accruedService = new AccruedInterestServiceImpl(
                new CouponScheduleService(couponDateGenerator));
        BondCashFlowService cashFlowService = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalService,
                couponService,
                accruedService);

        // STEP 1: Generate coupon dates
        System.out.println("\n[STEP 1: COUPON DATE GENERATION]");
        List<LocalDate> couponDates = couponDateGenerator.generate(bond, CALCULATION_DATE);
        System.out.println("Total coupon dates: " + couponDates.size());
        for (int i = 0; i < couponDates.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + couponDates.get(i));
        }

        // STEP 2: Generate principal repayments
        System.out.println("\n[STEP 2: PRINCIPAL REPAYMENT GENERATION]");
        List<PrincipalRepayment> repayments = principalService.generateRepayments(
                bond, couponDates, CALCULATION_DATE);
        System.out.println("Total repayments: " + repayments.size());
        for (int i = 0; i < repayments.size(); i++) {
            PrincipalRepayment rep = repayments.get(i);
            System.out.println("  " + (i + 1) + ". " + rep.date() 
                    + " : Repay=" + rep.principalAmount() 
                    + " Remaining=" + rep.remainingPrincipal());
        }

        // STEP 3: Calculate coupons
        System.out.println("\n[STEP 3: COUPON CALCULATION]");
        List<CouponPayment> coupons = couponService.calculateCoupons(
                bond, couponDates, repayments, CALCULATION_DATE);
        System.out.println("Total coupons: " + coupons.size());
        BigDecimal totalCouponsPaid = BigDecimal.ZERO;
        for (int i = 0; i < coupons.size(); i++) {
            CouponPayment coupon = coupons.get(i);
            System.out.println("  " + (i + 1) + ". " + coupon.date() 
                    + " : Amount=" + coupon.couponAmount() 
                    + " OnPrincipal=" + coupon.outstandingPrincipalBeforePayment());
            totalCouponsPaid = totalCouponsPaid.add(coupon.couponAmount());
        }
        System.out.println("Total coupons sum: " + totalCouponsPaid);

        // STEP 4: Calculate accrued interest
        System.out.println("\n[STEP 4: ACCRUED INTEREST CALCULATION]");
        BigDecimal accruedInterest = accruedService.calculate(bond, CALCULATION_DATE);
        System.out.println("Accrued Interest : " + accruedInterest);
        BigDecimal dirtyPrice = bond.getPrice().add(accruedInterest);
        System.out.println("Dirty Price      : " + dirtyPrice);

        // STEP 5: Generate complete cash flows
        System.out.println("\n[STEP 5: COMPLETE CASH FLOW TABLE]");
        List<XirrCalculator.CashFlow> cashFlows = cashFlowService.generateCashFlows(
                bond, CALCULATION_DATE);
        
        System.out.println("\nCash Flows:");
        System.out.println(String.format("%-12s | %20s", "Date", "Amount"));
        System.out.println("-".repeat(35));
        
        BigDecimal totalInflows = BigDecimal.ZERO;
        BigDecimal totalOutflows = BigDecimal.ZERO;
        
        for (XirrCalculator.CashFlow cf : cashFlows) {
            System.out.println(String.format("%-12s | %20s", cf.date(), cf.amount()));
            if (cf.amount().signum() > 0) {
                totalInflows = totalInflows.add(cf.amount());
            } else {
                totalOutflows = totalOutflows.add(cf.amount().abs());
            }
        }
        
        System.out.println("-".repeat(35));
        System.out.println("Total Inflows   : " + totalInflows);
        System.out.println("Total Outflows  : " + totalOutflows);
        System.out.println("Net             : " + totalInflows.subtract(totalOutflows));

        // STEP 6: Calculate XIRR
        System.out.println("\n[STEP 6: XIRR CALCULATION]");
        XirrCalculator xirrCalculator = new XirrCalculator();
        BigDecimal xirr = xirrCalculator.calculate(cashFlows);
        BigDecimal yitmPercentage = xirr.multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
        
        System.out.println("Calculated XIRR  : " + xirr);
        System.out.println("Calculated YTM % : " + yitmPercentage);
        System.out.println("Expected YTM %   : 10.04");
        System.out.println("Difference (bps) : " + yitmPercentage.subtract(new BigDecimal("10.04"))
                .multiply(new BigDecimal("100")));

        // STEP 7: Verify coupon calculations
        System.out.println("\n[STEP 7: COUPON VERIFICATION]");
        System.out.println("First coupon: " + (!coupons.isEmpty() ? coupons.get(0).couponAmount() : "N/A"));
        System.out.println("Should be: 100 * 7.20% / 2 = 3.60");
        
        // Calculate what first coupon should be
        BigDecimal expectedFirstCoupon = new BigDecimal("100")
                .multiply(bond.getCouponRate())
                .divide(new BigDecimal("200"), 10, RoundingMode.HALF_UP);
        System.out.println("Calculated expectation: " + expectedFirstCoupon);

        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT COMPLETE");
        System.out.println("=".repeat(100) + "\n");
    }

    private Bond createStagedBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Staged Amortization Audit Bond");
        bond.setIsin("AUDIT000001");
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
        return bond;
    }
}
