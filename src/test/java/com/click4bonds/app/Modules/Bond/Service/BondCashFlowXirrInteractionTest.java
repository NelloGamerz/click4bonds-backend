// package com.click4bonds.app.Modules.Bond.Service;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.when;

// import java.math.BigDecimal;
// import java.time.LocalDate;
// import java.time.temporal.ChronoUnit;
// import java.util.List;

// import org.junit.jupiter.api.Test;

// import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
// import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
// import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
// import com.click4bonds.app.Modules.Bond.Models.Bond;

// class BondCashFlowXirrInteractionTest {

//     private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);
//     private final XirrCalculator xirrCalculator = new XirrCalculator();

//     @Test
//     void calculatesXirrFromTheCompleteStagedAmortizationSchedule() {
//         Bond bond = fullBond();
//         BondCashFlowService service = realService();
//         List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

//         assertEquals(12, flows.size());
//         List<BigDecimal> expectedAmounts = List.of(
//             new BigDecimal("-94.50"), new BigDecimal("6.10"), new BigDecimal("6.01"),
//             new BigDecimal("5.92"), new BigDecimal("13.33"), new BigDecimal("12.97"),
//             new BigDecimal("12.61"), new BigDecimal("12.25"), new BigDecimal("11.89"),
//             new BigDecimal("11.53"), new BigDecimal("11.17"), new BigDecimal("23.31"));
//         for (int index = 0; index < expectedAmounts.size(); index++) {
//             assertEquals(0, expectedAmounts.get(index).compareTo(flows.get(index).amount()));
//         }

//         BigDecimal expected = BigDecimal.valueOf(independentXirr(flows)).setScale(6, java.math.RoundingMode.HALF_UP);
//         BigDecimal actual = xirrCalculator.calculate(flows);

//         System.out.println("========== XIRR TEST ==========");
//         System.out.println("Calculation Date: " + CALCULATION_DATE);
//         System.out.println("Purchase Price: " + bond.getPrice());
//         System.out.println("Number of Cash Flows: " + flows.size());
//         System.out.println("XIRR: " + actual);
//         System.out.println("===============================");

//         assertEquals(expected, actual);
//     }

//     @Test
//     void calculatesBulletBondYtmFromCouponsAndMaturityPrincipal() {
//         LocalDate maturity = CALCULATION_DATE.plusYears(2);
//         BondCashFlowService service = mockedService(
//                 List.of(maturity.minusYears(1), maturity),
//                 List.of(new PrincipalRepayment(maturity, new BigDecimal("100"), BigDecimal.ZERO)),
//                 List.of(new CouponPayment(maturity.minusYears(1), new BigDecimal("7"), new BigDecimal("100")),
//                         new CouponPayment(maturity, new BigDecimal("7"), new BigDecimal("100"))));

//         List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bondAtPrice("98"), CALCULATION_DATE);

//         assertEquals(List.of(new BigDecimal("-98"), new BigDecimal("7"), new BigDecimal("107")),
//                 flows.stream().map(XirrCalculator.CashFlow::amount).toList());
//         assertTrue(xirrCalculator.calculate(flows).doubleValue() > 0.07);
//     }

//     @Test
//     void preservesDecreasingAmortizingCouponsAndCalculatesTheirXirr() {
//         LocalDate firstDate = CALCULATION_DATE.plusMonths(6);
//         LocalDate secondDate = CALCULATION_DATE.plusYears(1);
//         BondCashFlowService service = mockedService(
//                 List.of(firstDate, secondDate),
//                 List.of(new PrincipalRepayment(firstDate, new BigDecimal("40"), new BigDecimal("60")),
//                         new PrincipalRepayment(secondDate, new BigDecimal("60"), BigDecimal.ZERO)),
//                 List.of(new CouponPayment(firstDate, new BigDecimal("6"), new BigDecimal("100")),
//                         new CouponPayment(secondDate, new BigDecimal("3.60"), new BigDecimal("60"))));

//         List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bondAtPrice("95"), CALCULATION_DATE);

//         assertTrue(flows.get(1).amount().compareTo(flows.get(2).amount()) < 0);
//         assertTrue(xirrCalculator.calculate(flows).doubleValue() > -1.0);
//     }

//     @Test
//     void consolidatesCouponAndPrincipalBeforeXirr() {
//         LocalDate paymentDate = CALCULATION_DATE.plusYears(1);
//         BondCashFlowService service = mockedService(
//                 List.of(paymentDate),
//                 List.of(new PrincipalRepayment(paymentDate, new BigDecimal("100"), BigDecimal.ZERO)),
//                 List.of(new CouponPayment(paymentDate, new BigDecimal("8"), new BigDecimal("100"))));

//         List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bondAtPrice("100"), CALCULATION_DATE);

//         assertEquals(2, flows.size());
//         assertEquals(new BigDecimal("108"), flows.get(1).amount());
//         assertEquals(new BigDecimal("0.080000"), xirrCalculator.calculate(flows));
//     }

//     @Test
//     void perpetualBondDoesNotInventMaturityPrincipal() {
//         LocalDate paymentDate = CALCULATION_DATE.plusYears(1);
//         BondCashFlowService service = mockedService(
//                 List.of(paymentDate), List.of(),
//                 List.of(new CouponPayment(paymentDate, new BigDecimal("7"), new BigDecimal("100"))));

//         List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bondAtPrice("90"), CALCULATION_DATE);

//         assertEquals(List.of(new BigDecimal("-90"), new BigDecimal("7")),
//                 flows.stream().map(XirrCalculator.CashFlow::amount).toList());
//         assertTrue(xirrCalculator.calculate(flows).doubleValue() > -1.0);
//     }

//     @Test
//     void lowerPriceProducesHigherYtmForTheSameSchedule() {
//         LocalDate maturity = CALCULATION_DATE.plusYears(1);
//         BondCashFlowService service = mockedService(
//                 List.of(maturity),
//                 List.of(new PrincipalRepayment(maturity, new BigDecimal("100"), BigDecimal.ZERO)),
//                 List.of(new CouponPayment(maturity, new BigDecimal("7.20"), new BigDecimal("100"))));

//         double ytmAt90 = xirrCalculator.calculate(service.generateCashFlows(bondAtPrice("90"), CALCULATION_DATE)).doubleValue();
//         double ytmAt9450 = xirrCalculator.calculate(service.generateCashFlows(bondAtPrice("94.50"), CALCULATION_DATE)).doubleValue();
//         double ytmAt100 = xirrCalculator.calculate(service.generateCashFlows(bondAtPrice("100"), CALCULATION_DATE)).doubleValue();
//         double ytmAt105 = xirrCalculator.calculate(service.generateCashFlows(bondAtPrice("105"), CALCULATION_DATE)).doubleValue();

//         assertTrue(ytmAt90 > ytmAt9450);
//         assertTrue(ytmAt9450 > ytmAt100);
//         assertTrue(ytmAt100 > ytmAt105);
//     }

//     private BondCashFlowService realService() {
//         return new BondCashFlowServiceImpl(new CouponDateGenerator(),
//                 new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl()),
//                 new CouponCalculationServiceImpl());
//     }

//     private BondCashFlowService mockedService(List<LocalDate> dates, List<PrincipalRepayment> repayments,
//             List<CouponPayment> coupons) {
//         CouponDateGenerator dateGenerator = mock(CouponDateGenerator.class);
//         PrincipalRepaymentService principalService = mock(PrincipalRepaymentService.class);
//         CouponCalculationService couponService = mock(CouponCalculationService.class);
//         when(dateGenerator.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(dates);
//         when(principalService.generateRepayments(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
//                 org.mockito.ArgumentMatchers.any())).thenReturn(repayments);
//         when(couponService.calculateCoupons(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
//                 org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(coupons);
//         return new BondCashFlowServiceImpl(dateGenerator, principalService, couponService);
//     }

//     private Bond bondAtPrice(String price) {
//         Bond bond = new Bond();
//         bond.setPrice(new BigDecimal(price));
//         bond.setCouponRate(new BigDecimal("7.20"));
//         bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
//         bond.setMaturityType(MaturityType.FIXED);
//         bond.setMaturityDate(CALCULATION_DATE.plusYears(1));
//         return bond;
//     }

//     private Bond fullBond() {
//         Bond bond = bondAtPrice("94.50");
//         bond.setIpDateDescription("26/03-26/09");
//         bond.setMaturityDate(LocalDate.of(2031, 9, 26));
//         bond.setMaturityDescription("26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
//         return bond;
//     }

//     private double independentXirr(List<XirrCalculator.CashFlow> flows) {
//         LocalDate start = flows.stream().map(XirrCalculator.CashFlow::date).min(LocalDate::compareTo).orElseThrow();
//         double lower = -0.9999999999;
//         double upper = 1.0;
//         while (npv(flows, start, lower) * npv(flows, start, upper) > 0) {
//             upper = 2 * (upper + 1) - 1;
//         }
//         for (int i = 0; i < 200; i++) {
//             double middle = (lower + upper) / 2;
//             if (npv(flows, start, lower) * npv(flows, start, middle) <= 0) {
//                 upper = middle;
//             } else {
//                 lower = middle;
//             }
//         }
//         return (lower + upper) / 2;
//     }

//     private double npv(List<XirrCalculator.CashFlow> flows, LocalDate start, double rate) {
//         return flows.stream().mapToDouble(flow -> flow.amount().doubleValue()
//                 / Math.pow(1 + rate, ChronoUnit.DAYS.between(start, flow.date()) / 365.0)).sum();
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

class BondCashFlowXirrInteractionTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    private final XirrCalculator xirrCalculator = new XirrCalculator();

    @Test
    void calculatesXirrFromTheCompleteStagedAmortizationSchedule() {
        System.out.println(
                "\n========== TEST: calculatesXirrFromTheCompleteStagedAmortizationSchedule ==========");

        Bond bond = fullBond();

        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("Bond Price: " + bond.getPrice());
        System.out.println("Coupon Rate: " + bond.getCouponRate());
        System.out.println("Coupon Frequency: " + bond.getCouponFrequency());
        System.out.println("Maturity Date: " + bond.getMaturityDate());
        System.out.println("Maturity Type: " + bond.getMaturityType());
        System.out.println("IP Date Description: " + bond.getIpDateDescription());
        System.out.println("Maturity Description: " + bond.getMaturityDescription());

        BondCashFlowService service = realService();

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated Cash Flows:");
        flows.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        assertEquals(12, flows.size());

        List<BigDecimal> expectedAmounts = List.of(
                new BigDecimal("-96.08794520547945205479"),
                new BigDecimal("6.10"),
                new BigDecimal("6.01"),
                new BigDecimal("5.92"),
                new BigDecimal("13.33"),
                new BigDecimal("12.97"),
                new BigDecimal("12.61"),
                new BigDecimal("12.25"),
                new BigDecimal("11.89"),
                new BigDecimal("11.53"),
                new BigDecimal("11.17"),
                new BigDecimal("23.31"));

        for (int index = 0; index < expectedAmounts.size(); index++) {
            System.out.println(
                    "Checking flow " + index
                            + " | Expected: " + expectedAmounts.get(index)
                            + " | Actual: " + flows.get(index).amount());

            assertEquals(
                    0,
                    expectedAmounts.get(index)
                            .compareTo(flows.get(index).amount()));
        }

        double independentResult = independentXirr(flows);

        BigDecimal expected = BigDecimal.valueOf(independentResult)
                .setScale(
                        6,
                        java.math.RoundingMode.HALF_UP);

        BigDecimal actual = xirrCalculator.calculate(flows);

        System.out.println("Independent XIRR: " + independentResult);
        System.out.println("Expected XIRR: " + expected);
        System.out.println("Actual XIRR: " + actual);

        assertEquals(expected, actual);

        System.out.println(
                "PASS: Complete staged amortization schedule XIRR calculated successfully.");
        System.out.println(
                "================================================================================");
    }

    @Test
    void calculatesBulletBondYtmFromCouponsAndMaturityPrincipal() {
        System.out.println(
                "\n========== TEST: calculatesBulletBondYtmFromCouponsAndMaturityPrincipal ==========");

        LocalDate maturity = CALCULATION_DATE.plusYears(2);

        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("Maturity Date: " + maturity);
        System.out.println("Purchase Price: 98");
        System.out.println("Maturity Principal: 100");
        System.out.println("Annual Coupon: 7");

        BondCashFlowService service = mockedService(
                List.of(
                        maturity.minusYears(1),
                        maturity),
                List.of(
                        new PrincipalRepayment(
                                maturity,
                                new BigDecimal("100"),
                                BigDecimal.ZERO)),
                List.of(
                        new CouponPayment(
                                maturity.minusYears(1),
                                new BigDecimal("7"),
                                new BigDecimal("100")),
                        new CouponPayment(
                                maturity,
                                new BigDecimal("7"),
                                new BigDecimal("100"))));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(
                bondAtPrice("98"),
                CALCULATION_DATE);

        System.out.println("Generated Cash Flows:");

        flows.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        BigDecimal xirr = xirrCalculator.calculate(flows);

        System.out.println("Calculated YTM/XIRR: " + xirr);
        System.out.println("Calculated YTM/XIRR (%): "
                + xirr.multiply(new BigDecimal("100")) + "%");

        assertEquals(
                List.of(
                        new BigDecimal("-98"),
                        new BigDecimal("7"),
                        new BigDecimal("107")),
                flows.stream()
                        .map(XirrCalculator.CashFlow::amount)
                        .toList());

        assertTrue(
                xirr.doubleValue() > 0.07);

        System.out.println(
                "PASS: Bullet bond YTM calculated successfully.");
        System.out.println(
                "================================================================================");
    }

    @Test
    void preservesDecreasingAmortizingCouponsAndCalculatesTheirXirr() {
        System.out.println(
                "\n========== TEST: preservesDecreasingAmortizingCouponsAndCalculatesTheirXirr ==========");

        LocalDate firstDate = CALCULATION_DATE.plusMonths(6);

        LocalDate secondDate = CALCULATION_DATE.plusYears(1);

        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("First Payment Date: " + firstDate);
        System.out.println("Second Payment Date: " + secondDate);
        System.out.println("Bond Price: 95");

        BondCashFlowService service = mockedService(
                List.of(
                        firstDate,
                        secondDate),
                List.of(
                        new PrincipalRepayment(
                                firstDate,
                                new BigDecimal("40"),
                                new BigDecimal("60")),
                        new PrincipalRepayment(
                                secondDate,
                                new BigDecimal("60"),
                                BigDecimal.ZERO)),
                List.of(
                        new CouponPayment(
                                firstDate,
                                new BigDecimal("6"),
                                new BigDecimal("100")),
                        new CouponPayment(
                                secondDate,
                                new BigDecimal("3.60"),
                                new BigDecimal("60"))));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(
                bondAtPrice("95"),
                CALCULATION_DATE);

        System.out.println("Generated Cash Flows:");

        flows.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        BigDecimal xirr = xirrCalculator.calculate(flows);

        System.out.println("Calculated XIRR: " + xirr);
        System.out.println(
                "Calculated XIRR (%): "
                        + xirr.multiply(new BigDecimal("100"))
                        + "%");

        System.out.println(
                "Comparing first payment amount: "
                        + flows.get(1).amount()
                        + " vs second payment amount: "
                        + flows.get(2).amount());

        assertTrue(
                flows.get(1)
                        .amount()
                        .compareTo(flows.get(2).amount()) < 0);

        assertTrue(
                xirr.doubleValue() > -1.0);

        System.out.println(
                "PASS: Decreasing amortizing coupons preserved and XIRR calculated.");
        System.out.println(
                "================================================================================");
    }

    @Test
    void consolidatesCouponAndPrincipalBeforeXirr() {
        System.out.println(
                "\n========== TEST: consolidatesCouponAndPrincipalBeforeXirr ==========");

        LocalDate paymentDate = CALCULATION_DATE.plusYears(1);

        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("Payment Date: " + paymentDate);
        System.out.println("Purchase Price: 100");
        System.out.println("Coupon: 8");
        System.out.println("Principal: 100");

        BondCashFlowService service = mockedService(
                List.of(paymentDate),
                List.of(
                        new PrincipalRepayment(
                                paymentDate,
                                new BigDecimal("100"),
                                BigDecimal.ZERO)),
                List.of(
                        new CouponPayment(
                                paymentDate,
                                new BigDecimal("8"),
                                new BigDecimal("100"))));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(
                bondAtPrice("100"),
                CALCULATION_DATE);

        System.out.println("Generated Cash Flows:");

        flows.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        BigDecimal xirr = xirrCalculator.calculate(flows);

        System.out.println("Calculated XIRR: " + xirr);
        System.out.println(
                "Calculated XIRR (%): "
                        + xirr.multiply(new BigDecimal("100"))
                        + "%");

        assertEquals(
                2,
                flows.size());

        assertEquals(
                new BigDecimal("108"),
                flows.get(1).amount());

        assertEquals(
                new BigDecimal("0.080000"),
                xirr);

        System.out.println(
                "PASS: Coupon and principal consolidated before XIRR.");
        System.out.println(
                "================================================================================");
    }

    @Test
    void perpetualBondDoesNotInventMaturityPrincipal() {
        System.out.println(
                "\n========== TEST: perpetualBondDoesNotInventMaturityPrincipal ==========");

        LocalDate paymentDate = CALCULATION_DATE.plusYears(1);

        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("Payment Date: " + paymentDate);
        System.out.println("Purchase Price: 90");
        System.out.println("Coupon: 7");
        System.out.println("Expected: No maturity principal");

        BondCashFlowService service = mockedService(
                List.of(paymentDate),
                List.of(),
                List.of(
                        new CouponPayment(
                                paymentDate,
                                new BigDecimal("7"),
                                new BigDecimal("100"))));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(
                bondAtPrice("90"),
                CALCULATION_DATE);

        System.out.println("Generated Cash Flows:");

        flows.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        BigDecimal xirr = xirrCalculator.calculate(flows);

        System.out.println("Calculated XIRR: " + xirr);
        System.out.println(
                "Calculated XIRR (%): "
                        + xirr.multiply(new BigDecimal("100"))
                        + "%");

        assertEquals(
                List.of(
                        new BigDecimal("-90"),
                        new BigDecimal("7")),
                flows.stream()
                        .map(XirrCalculator.CashFlow::amount)
                        .toList());

        assertTrue(
                xirr.doubleValue() > -1.0);

        System.out.println(
                "PASS: Perpetual bond did not invent maturity principal.");
        System.out.println(
                "================================================================================");
    }

    @Test
    void lowerPriceProducesHigherYtmForTheSameSchedule() {
        System.out.println(
                "\n========== TEST: lowerPriceProducesHigherYtmForTheSameSchedule ==========");

        LocalDate maturity = CALCULATION_DATE.plusYears(1);

        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("Maturity Date: " + maturity);
        System.out.println("Coupon: 7.20");
        System.out.println("Principal: 100");

        BondCashFlowService service = mockedService(
                List.of(maturity),
                List.of(
                        new PrincipalRepayment(
                                maturity,
                                new BigDecimal("100"),
                                BigDecimal.ZERO)),
                List.of(
                        new CouponPayment(
                                maturity,
                                new BigDecimal("7.20"),
                                new BigDecimal("100"))));

        List<XirrCalculator.CashFlow> flowsAt90 = service.generateCashFlows(
                bondAtPrice("90"),
                CALCULATION_DATE);

        List<XirrCalculator.CashFlow> flowsAt9450 = service.generateCashFlows(
                bondAtPrice("94.50"),
                CALCULATION_DATE);

        List<XirrCalculator.CashFlow> flowsAt100 = service.generateCashFlows(
                bondAtPrice("100"),
                CALCULATION_DATE);

        List<XirrCalculator.CashFlow> flowsAt105 = service.generateCashFlows(
                bondAtPrice("105"),
                CALCULATION_DATE);

        double ytmAt90 = xirrCalculator.calculate(flowsAt90).doubleValue();

        double ytmAt9450 = xirrCalculator.calculate(flowsAt9450).doubleValue();

        double ytmAt100 = xirrCalculator.calculate(flowsAt100).doubleValue();

        double ytmAt105 = xirrCalculator.calculate(flowsAt105).doubleValue();

        System.out.println("\nCash Flows @ 90:");
        flowsAt90.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        System.out.println("\nCash Flows @ 94.50:");
        flowsAt9450.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        System.out.println("\nCash Flows @ 100:");
        flowsAt100.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        System.out.println("\nCash Flows @ 105:");
        flowsAt105.forEach(flow -> System.out.println(
                "  Date: " + flow.date()
                        + " | Amount: " + flow.amount()));

        System.out.println("\nYTM Comparison:");
        System.out.println("Price 90.00  -> YTM: " + ytmAt90);
        System.out.println("Price 94.50  -> YTM: " + ytmAt9450);
        System.out.println("Price 100.00  -> YTM: " + ytmAt100);
        System.out.println("Price 105.00  -> YTM: " + ytmAt105);

        System.out.println(
                "\nExpected relationship:");
        System.out.println(
                "YTM(90) > YTM(94.50) > YTM(100) > YTM(105)");

        assertTrue(
                ytmAt90 > ytmAt9450);

        assertTrue(
                ytmAt9450 > ytmAt100);

        assertTrue(
                ytmAt100 > ytmAt105);

        System.out.println(
                "PASS: Lower bond price correctly produces higher YTM.");
        System.out.println(
                "================================================================================");
    }

    private BondCashFlowService realService() {
        return new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));
    }

    private BondCashFlowService mockedService(
            List<LocalDate> dates,
            List<PrincipalRepayment> repayments,
            List<CouponPayment> coupons) {
        System.out.println("\n--- Setting up mocked BondCashFlowService ---");
        System.out.println("Coupon dates: " + dates);
        System.out.println("Principal repayments: " + repayments);
        System.out.println("Coupon payments: " + coupons);

        CouponDateGenerator dateGenerator = mock(CouponDateGenerator.class);

        PrincipalRepaymentService principalService = mock(PrincipalRepaymentService.class);

        CouponCalculationService couponService = mock(CouponCalculationService.class);

        when(
                dateGenerator.generate(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(dates);

        when(
                principalService.generateRepayments(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(repayments);

        when(
                couponService.calculateCoupons(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(coupons);

        System.out.println("Mocked BondCashFlowService configured successfully.");

        return new BondCashFlowServiceImpl(
                dateGenerator,
                principalService,
                couponService,
                new AccruedInterestServiceImpl(new CouponScheduleService(dateGenerator)));
    }

    private Bond bondAtPrice(String price) {
        Bond bond = new Bond();

        bond.setPrice(
                new BigDecimal(price));

        bond.setCouponRate(
                new BigDecimal("7.20"));

        bond.setCouponFrequency(
                CouponFrequency.HALF_YEARLY);

        bond.setMaturityType(
                MaturityType.FIXED);

        bond.setMaturityDate(
                CALCULATION_DATE.plusYears(1));

        return bond;
    }

    private Bond fullBond() {
        Bond bond = bondAtPrice("94.50");

        bond.setIpDateDescription(
                "26/03-26/09");

        bond.setMaturityDate(
                LocalDate.of(2031, 9, 26));

        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");

        return bond;
    }

    private double independentXirr(
            List<XirrCalculator.CashFlow> flows) {
        System.out.println(
                "\n--- Calculating Independent XIRR ---");

        LocalDate start = flows.stream()
                .map(XirrCalculator.CashFlow::date)
                .min(LocalDate::compareTo)
                .orElseThrow();

        System.out.println("Independent XIRR Start Date: " + start);

        double lower = -0.9999999999;
        double upper = 1.0;

        System.out.println(
                "Initial lower bound: " + lower);

        System.out.println(
                "Initial upper bound: " + upper);

        int expansionCount = 0;

        while (npv(flows, start, lower)
                * npv(flows, start, upper) > 0) {
            upper = 2 * (upper + 1) - 1;
            expansionCount++;

            System.out.println(
                    "Expanding upper bound to: "
                            + upper);

            if (expansionCount > 100) {
                throw new IllegalStateException(
                        "Unable to find XIRR bracket");
            }
        }

        System.out.println(
                "Final XIRR bracket: ["
                        + lower
                        + ", "
                        + upper
                        + "]");

        for (int i = 0; i < 200; i++) {
            double middle = (lower + upper) / 2;

            if (npv(flows, start, lower)
                    * npv(flows, start, middle) <= 0) {
                upper = middle;
            } else {
                lower = middle;
            }
        }

        double result = (lower + upper) / 2;

        System.out.println(
                "Independent XIRR result: " + result);

        return result;
    }

    private double npv(
            List<XirrCalculator.CashFlow> flows,
            LocalDate start,
            double rate) {
        return flows.stream()
                .mapToDouble(
                        flow -> flow.amount().doubleValue()
                                / Math.pow(
                                        1 + rate,
                                        ChronoUnit.DAYS.between(
                                                start,
                                                flow.date()) / 365.0))
                .sum();
    }
}
