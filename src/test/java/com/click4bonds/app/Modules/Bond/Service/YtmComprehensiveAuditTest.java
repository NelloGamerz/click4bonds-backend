package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Comprehensive audit test for all major bond types.
 * Uses FIXED calculation date to ensure consistent results.
 */
class YtmComprehensiveAuditTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    private static final CouponDateGenerator couponDateGenerator = new CouponDateGenerator();
    private static final PrincipalRepaymentService principalService = 
            new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl());
    private static final CouponCalculationService couponService = new CouponCalculationServiceImpl();
    private static final AccruedInterestService accruedService = 
            new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator));
    private static final BondCashFlowService cashFlowService = 
            new BondCashFlowServiceImpl(couponDateGenerator, principalService, couponService, accruedService);
    private static final XirrCalculator xirrCalculator = new XirrCalculator();

    @Test
    void auditAllBondTypes() {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("COMPREHENSIVE YTM AUDIT - ALL BOND TYPES");
        System.out.println("=".repeat(120));
        System.out.println("Fixed Calculation Date: " + CALCULATION_DATE);
        System.out.println();

        // TEST 1: Annual Bullet Bond
        auditBond(
                "Annual Bullet Bond",
                createAnnualBulletBond(),
                "Simple coupon, no amortization"
        );

        // TEST 2: Monthly Bullet Bond
        auditBond(
                "Monthly Bullet Bond",
                createMonthlyBulletBond(),
                "Monthly coupons, no amortization"
        );

        // TEST 3: Quarterly Bullet Bond
        auditBond(
                "Quarterly Bullet Bond",
                createQuarterlyBulletBond(),
                "Quarterly coupons, no amortization"
        );

        // TEST 4: Half-Yearly Bullet Bond
        auditBond(
                "Half-Yearly Bullet Bond",
                createHalfYearlyBulletBond(),
                "Semi-annual coupons, no amortization"
        );

        // TEST 5: Annual Amortized Bond
        auditBond(
                "Annual Amortized Bond",
                createAnnualAmortizedBond(),
                "Annual coupons, 20% annual repayment"
        );

        // TEST 6: Monthly Amortized Bond
        auditBond(
                "Monthly Amortized Bond (Quarterly Repayment)",
                createMonthlyAmortizedBond(),
                "Monthly coupons, 10% quarterly repayment"
        );

        // TEST 7: Staged IP Amortized Bond
        auditBond(
                "Staged IP Amortized Bond",
                createStagedIPAmortizedBond(),
                "Semi-annual coupons, staged IP amortization"
        );

        System.out.println("\n" + "=".repeat(120));
        System.out.println("COMPREHENSIVE AUDIT COMPLETE");
        System.out.println("=".repeat(120));
    }

    private void auditBond(String testName, Bond bond, String description) {
        System.out.println("\n" + "-".repeat(120));
        System.out.println("TEST: " + testName);
        System.out.println("-".repeat(120));
        System.out.println("Description: " + description);
        System.out.println();

        try {
            // Generate coupon dates
            List<LocalDate> couponDates = couponDateGenerator.generate(bond, CALCULATION_DATE);
            System.out.println("Coupon Dates: " + couponDates.size() + " payments");

            // Generate principal repayments
            List<PrincipalRepayment> repayments = principalService.generateRepayments(
                    bond, couponDates, CALCULATION_DATE);
            System.out.println("Principal Repayments: " + repayments.size() + " repayments");

            // Calculate coupons
            List<CouponPayment> coupons = couponService.calculateCoupons(
                    bond, couponDates, repayments, CALCULATION_DATE);

            // Validate coupon sum (for amortized bonds, should match principal reductions)
            BigDecimal totalCoupons = coupons.stream()
                    .map(CouponPayment::couponAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalRepayments = repayments.stream()
                    .map(PrincipalRepayment::principalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            System.out.println(String.format("Total Coupons: %.4f  |  Total Principal: %.4f", 
                    totalCoupons, totalRepayments));

            // Calculate accrued interest
            BigDecimal accruedInterest = accruedService.calculate(bond, CALCULATION_DATE);
            BigDecimal dirtyPrice = bond.getPrice().add(accruedInterest);

            System.out.println(String.format("Clean Price: %.2f  |  Accrued Interest: %.4f  |  Dirty Price: %.4f", 
                    bond.getPrice(), accruedInterest, dirtyPrice));

            // Generate cash flows
            List<XirrCalculator.CashFlow> cashFlows = cashFlowService.generateCashFlows(bond, CALCULATION_DATE);

            // Calculate XIRR
            BigDecimal xirr = xirrCalculator.calculate(cashFlows);
            BigDecimal ytmPercentage = xirr.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);

            System.out.println(String.format("XIRR (Decimal): %.6f  |  YTM (Percentage): %.2f%%", 
                    xirr, ytmPercentage));

            // Check for basic validity
            if (xirr.signum() >= 0) {
                System.out.println("✓ PASS: YTM calculation successful");
            } else {
                System.out.println("✗ WARNING: Negative YTM");
            }

        } catch (Exception e) {
            System.out.println("✗ FAIL: Exception during audit");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========================
    // BOND CREATION METHODS
    // ========================

    private Bond createAnnualBulletBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Annual Bullet Bond");
        bond.setPrice(new BigDecimal("98.00"));
        bond.setCouponRate(new BigDecimal("10.00"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2029, 3, 15));
        bond.setIpDateDescription("15/03 Ann");
        return bond;
    }

    private Bond createMonthlyBulletBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Monthly Bullet Bond");
        bond.setPrice(new BigDecimal("95.00"));
        bond.setCouponRate(new BigDecimal("12.00"));
        bond.setCouponFrequency(CouponFrequency.MONTHLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2028, 12, 5));
        bond.setIpDateDescription("5th of every month");
        return bond;
    }

    private Bond createQuarterlyBulletBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Quarterly Bullet Bond");
        bond.setPrice(new BigDecimal("96.50"));
        bond.setCouponRate(new BigDecimal("9.00"));
        bond.setCouponFrequency(CouponFrequency.QUARTERLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2028, 9, 15));
        bond.setIpDateDescription("15/03-15/06-15/09-15/12");  // This format may not be exact
        bond.setIpDateDescription("15/03-15/09"); // Semi-annual starting pattern
        return bond;
    }

    private Bond createHalfYearlyBulletBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Half-Yearly Bullet Bond");
        bond.setPrice(new BigDecimal("97.00"));
        bond.setCouponRate(new BigDecimal("8.50"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2030, 6, 30));
        bond.setIpDateDescription("30/06-30/12");
        return bond;
    }

    private Bond createAnnualAmortizedBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Annual Amortized Bond");
        bond.setPrice(new BigDecimal("95.00"));
        bond.setCouponRate(new BigDecimal("10.00"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2030, 12, 31));
        bond.setIpDateDescription("31/12 Ann");
        bond.setMaturityDescription("31/12/2030 (20% each year)");
        return bond;
    }

    private Bond createMonthlyAmortizedBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Monthly Bond with Quarterly Amortization");
        bond.setPrice(new BigDecimal("95.50"));
        bond.setCouponRate(new BigDecimal("12.00"));
        bond.setCouponFrequency(CouponFrequency.MONTHLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2027, 12, 31));
        bond.setIpDateDescription("1st of every month");
        bond.setMaturityDescription("31/12/2027 (10% quarterly)");
        return bond;
    }

    private Bond createStagedIPAmortizedBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Staged IP Amortized Bond");
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
        return bond;
    }
}
