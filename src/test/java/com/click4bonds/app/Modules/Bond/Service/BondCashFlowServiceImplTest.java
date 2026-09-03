package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

class BondCashFlowServiceImplTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    @Test
    void shouldBuildCompleteCashFlowScheduleForBond() {
        System.out.println("\n========== TEST: shouldBuildCompleteCashFlowScheduleForBond ==========");

        Bond bond = fullBond();
        CouponDateGenerator couponDateGenerator = new CouponDateGenerator();
        PrincipalRepaymentService principalRepaymentService = new PrincipalRepaymentServiceImpl(
                new MaturityDescriptionParserImpl());
        CouponCalculationService couponCalculationService = new CouponCalculationServiceImpl();

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(12, flows.size());
        assertEquals(LocalDate.of(2026, 9, 3), flows.get(0).date());
        assertEquals(new BigDecimal("-96.08794520547945205479"), flows.get(0).amount());

        assertEquals(List.of(
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 26),
                LocalDate.of(2027, 3, 26),
                LocalDate.of(2027, 9, 26),
                LocalDate.of(2028, 3, 26),
                LocalDate.of(2028, 9, 26),
                LocalDate.of(2029, 3, 26),
                LocalDate.of(2029, 9, 26),
                LocalDate.of(2030, 3, 26),
                LocalDate.of(2030, 9, 26),
                LocalDate.of(2031, 3, 26),
                LocalDate.of(2031, 9, 26)), flows.stream().map(XirrCalculator.CashFlow::date).toList());

        List<BigDecimal> actualAmounts = flows.stream()
                .map(XirrCalculator.CashFlow::amount)
                .toList();

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

        assertEquals(expectedAmounts.size(), actualAmounts.size());

        for (int i = 0; i < expectedAmounts.size(); i++) {
            assertEquals(
                    0,
                    expectedAmounts.get(i).compareTo(actualAmounts.get(i)));
        }

        assertTrue(flows.get(0).amount().signum() < 0);
        assertTrue(flows.stream().skip(1).allMatch(
                flow -> flow.amount().signum() > 0));

        System.out.println("PASS: Complete cash flow schedule generated successfully.");
    }

    @Test
    void shouldConsolidateCouponAndPrincipalOnSameDateIntoOneCashFlow() {
        System.out
                .println("\n========== TEST: shouldConsolidateCouponAndPrincipalOnSameDateIntoOneCashFlow ==========");

        Bond bond = basicBond();
        LocalDate date = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(date));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(date),
                CALCULATION_DATE)).thenReturn(List.of(
                        new PrincipalRepayment(
                                date,
                                new BigDecimal("20.00"),
                                BigDecimal.ZERO)));

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(date),
                List.of(new PrincipalRepayment(
                        date,
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO)),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                date,
                                new BigDecimal("3.50"),
                                new BigDecimal("100.00"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(2, flows.size());
        assertEquals(
                List.of(LocalDate.of(2026, 9, 3), date),
                flows.stream()
                        .map(XirrCalculator.CashFlow::date)
                        .toList());
        assertEquals(new BigDecimal("23.50"), flows.get(1).amount());

        System.out.println("PASS: Coupon and principal consolidated successfully.");
    }

    @Test
    void shouldHandleCouponOnlyDate() {
        System.out.println("\n========== TEST: shouldHandleCouponOnlyDate ==========");

        Bond bond = basicBond();
        LocalDate date = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(date));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(date),
                CALCULATION_DATE)).thenReturn(List.of());

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(date),
                List.of(),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                date,
                                new BigDecimal("3.60"),
                                new BigDecimal("100.00"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(2, flows.size());
        assertEquals(new BigDecimal("3.60"), flows.get(1).amount());

        System.out.println("PASS: Coupon-only date handled successfully.");
    }

    @Test
    void shouldHandlePrincipalOnlyDate() {
        System.out.println("\n========== TEST: shouldHandlePrincipalOnlyDate ==========");

        Bond bond = basicBond();
        LocalDate date = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(date));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(date),
                CALCULATION_DATE)).thenReturn(List.of(
                        new PrincipalRepayment(
                                date,
                                new BigDecimal("20.00"),
                                BigDecimal.ZERO)));

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(date),
                List.of(new PrincipalRepayment(
                        date,
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO)),
                CALCULATION_DATE)).thenReturn(List.of());

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(2, flows.size());
        assertEquals(new BigDecimal("20.00"), flows.get(1).amount());

        System.out.println("PASS: Principal-only date handled successfully.");
    }

    @Test
    void shouldUseBondPriceForInitialCashFlowAndNotFaceValue() {
        System.out.println("\n========== TEST: shouldUseBondPriceForInitialCashFlowAndNotFaceValue ==========");

        Bond bond = basicBond();
        bond.setPrice(new BigDecimal("94.50"));

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of());

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(),
                CALCULATION_DATE)).thenReturn(List.of());

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(),
                List.of(),
                CALCULATION_DATE)).thenReturn(List.of());

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(1, flows.size());
        assertEquals(
                LocalDate.of(2026, 9, 3),
                flows.get(0).date());
        assertEquals(
                new BigDecimal("-94.50"),
                flows.get(0).amount());

        System.out.println("PASS: Bond price used for initial cash flow.");
    }

    @Test
    void shouldSortCashFlowsChronologicallyWhenInputsAreUnordered() {
        System.out.println("\n========== TEST: shouldSortCashFlowsChronologicallyWhenInputsAreUnordered ==========");

        Bond bond = basicBond();

        LocalDate early = LocalDate.of(2026, 9, 26);
        LocalDate middle = LocalDate.of(2027, 9, 26);
        LocalDate late = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);

        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(middle, late, early));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(middle, late, early),
                CALCULATION_DATE)).thenReturn(List.of(
                        new PrincipalRepayment(
                                early,
                                new BigDecimal("2.50"),
                                new BigDecimal("97.50"))));

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(middle, late, early),
                List.of(new PrincipalRepayment(
                        early,
                        new BigDecimal("2.50"),
                        new BigDecimal("97.50"))),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                middle,
                                new BigDecimal("3.42"),
                                new BigDecimal("95.00")),
                        new CouponPayment(
                                late,
                                new BigDecimal("3.51"),
                                new BigDecimal("97.50")),
                        new CouponPayment(
                                early,
                                new BigDecimal("3.60"),
                                new BigDecimal("100.00"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        List<LocalDate> dates = flows.stream()
                .map(XirrCalculator.CashFlow::date)
                .toList();

        System.out.println("Sorted cash flow dates: " + dates);

        assertEquals(List.of(
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 26),
                LocalDate.of(2027, 3, 26),
                LocalDate.of(2027, 9, 26)), dates);

        System.out.println("PASS: Cash flows sorted chronologically.");
    }

    @Test
    void shouldHandleDuplicateSameDateEntriesWithoutDuplicatingCashFlow() {
        System.out.println(
                "\n========== TEST: shouldHandleDuplicateSameDateEntriesWithoutDuplicatingCashFlow ==========");

        Bond bond = basicBond();
        LocalDate date = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);

        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(date));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(date),
                CALCULATION_DATE)).thenReturn(List.of(
                        new PrincipalRepayment(
                                date,
                                new BigDecimal("10.00"),
                                new BigDecimal("90.00"))));

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(date),
                List.of(new PrincipalRepayment(
                        date,
                        new BigDecimal("10.00"),
                        new BigDecimal("90.00"))),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                date,
                                new BigDecimal("3.00"),
                                new BigDecimal("100.00")),
                        new CouponPayment(
                                date,
                                new BigDecimal("0.50"),
                                new BigDecimal("100.00"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(2, flows.size());
        assertEquals(
                new BigDecimal("13.50"),
                flows.get(1).amount());

        System.out.println("PASS: Duplicate same-date entries consolidated successfully.");
    }

    @Test
    void shouldNotInventMaturityPaymentWhenNotSupplied() {
        System.out.println("\n========== TEST: shouldNotInventMaturityPaymentWhenNotSupplied ==========");

        Bond bond = basicBond();
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));

        LocalDate couponDate = LocalDate.of(2031, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);

        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(couponDate));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(couponDate),
                CALCULATION_DATE)).thenReturn(List.of());

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(couponDate),
                List.of(),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                couponDate,
                                new BigDecimal("1.17"),
                                new BigDecimal("22.50"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertFalse(flows.stream()
                .anyMatch(flow -> flow.date().equals(LocalDate.of(2031, 9, 26))));

        System.out.println("PASS: No maturity payment invented.");
    }

    @Test
    void shouldSupportPerpetualBondWithoutAddingFaceValueMaturity() {
        System.out.println("\n========== TEST: shouldSupportPerpetualBondWithoutAddingFaceValueMaturity ==========");

        Bond bond = basicBond();
        bond.setMaturityType(MaturityType.PERPETUAL);

        LocalDate future = LocalDate.of(2027, 9, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);

        when(couponDateGenerator.generate(bond, CALCULATION_DATE))
                .thenReturn(List.of(future));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(future),
                CALCULATION_DATE)).thenReturn(List.of());

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(future),
                List.of(),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                future,
                                new BigDecimal("3.42"),
                                new BigDecimal("100.00"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertEquals(2, flows.size());
        assertEquals(
                new BigDecimal("-94.50"),
                flows.get(0).amount());
        assertEquals(
                new BigDecimal("3.42"),
                flows.get(1).amount());
        assertFalse(flows.stream()
                .anyMatch(flow -> flow.amount().compareTo(new BigDecimal("100")) == 0));

        System.out.println("PASS: Perpetual bond handled without maturity payment.");
    }

    @Test
    void shouldExcludePaymentsOnOrBeforeCalculationDate() {
        System.out.println("\n========== TEST: shouldExcludePaymentsOnOrBeforeCalculationDate ==========");

        Bond bond = basicBond();

        LocalDate sameDay = CALCULATION_DATE;
        LocalDate futureDate = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);

        when(couponDateGenerator.generate(
                bond,
                CALCULATION_DATE)).thenReturn(List.of(sameDay, futureDate));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);

        when(principalRepaymentService.generateRepayments(
                bond,
                List.of(sameDay, futureDate),
                CALCULATION_DATE)).thenReturn(List.of(
                        new PrincipalRepayment(
                                sameDay,
                                new BigDecimal("2.50"),
                                new BigDecimal("97.50")),
                        new PrincipalRepayment(
                                futureDate,
                                new BigDecimal("20.00"),
                                new BigDecimal("77.50"))));

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);

        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(sameDay, futureDate),
                List.of(
                        new PrincipalRepayment(
                                sameDay,
                                new BigDecimal("2.50"),
                                new BigDecimal("97.50")),
                        new PrincipalRepayment(
                                futureDate,
                                new BigDecimal("20.00"),
                                new BigDecimal("77.50"))),
                CALCULATION_DATE)).thenReturn(List.of(
                        new CouponPayment(
                                futureDate,
                                new BigDecimal("3.60"),
                                new BigDecimal("100.00"))));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);

        System.out.println("Generated cash flows: " + flows);

        assertFalse(flows.stream()
                .anyMatch(flow -> flow.date().equals(sameDay)
                        && flow.amount().signum() > 0));

        assertTrue(flows.stream()
                .anyMatch(flow -> flow.date().equals(futureDate)));

        System.out.println("PASS: Payments on/before calculation date excluded.");
    }

    @Test
    void shouldVerifyDownstreamOrchestrationDependencies() {
        System.out.println("\n========== TEST: shouldVerifyDownstreamOrchestrationDependencies ==========");

        Bond bond = basicBond();
        LocalDate date = LocalDate.of(2027, 3, 26);
        List<LocalDate> couponDates = List.of(date);
        List<PrincipalRepayment> principalRepayments = List.of(
                new PrincipalRepayment(date, new BigDecimal("20.00"), BigDecimal.ZERO));
        List<CouponPayment> couponPayments = List.of(
                new CouponPayment(date, new BigDecimal("3.50"), new BigDecimal("100.00")));

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE)).thenReturn(couponDates);

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);
        when(principalRepaymentService.generateRepayments(bond, couponDates, CALCULATION_DATE))
                .thenReturn(principalRepayments);

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);
        when(couponCalculationService.calculateCoupons(bond, couponDates, principalRepayments, CALCULATION_DATE))
                .thenReturn(couponPayments);

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);
        System.out.println("Generated cash flows: " + flows);

        verify(couponDateGenerator).generate(bond, CALCULATION_DATE);
        verify(principalRepaymentService).generateRepayments(bond, couponDates, CALCULATION_DATE);
        verify(couponCalculationService).calculateCoupons(bond, couponDates, principalRepayments, CALCULATION_DATE);
        assertEquals(2, flows.size());

        System.out.println("PASS: Orchestration dependencies invoked as expected.");
    }

    @Test
    void shouldHandleNullDependencyResultsAsEmptyLists() {
        System.out.println("\n========== TEST: shouldHandleNullDependencyResultsAsEmptyLists ==========");

        Bond bond = basicBond();
        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE)).thenReturn(null);

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);
        when(principalRepaymentService.generateRepayments(bond, List.of(), CALCULATION_DATE)).thenReturn(null);

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);
        when(couponCalculationService.calculateCoupons(bond, List.of(), List.of(), CALCULATION_DATE)).thenReturn(null);

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);
        System.out.println("Generated cash flows: " + flows);

        assertEquals(1, flows.size());
        assertEquals(new BigDecimal("-94.50"), flows.get(0).amount());

        System.out.println("PASS: Null dependency results treated as empty lists.");
    }

    @Test
    void shouldIgnoreNullEntriesInsideDependencyResults() {
        System.out.println("\n========== TEST: shouldIgnoreNullEntriesInsideDependencyResults ==========");

        Bond bond = basicBond();
        LocalDate date = LocalDate.of(2027, 3, 26);

        CouponDateGenerator couponDateGenerator = mock(CouponDateGenerator.class);
        when(couponDateGenerator.generate(bond, CALCULATION_DATE)).thenReturn(List.of(date));

        PrincipalRepaymentService principalRepaymentService = mock(PrincipalRepaymentService.class);
        when(principalRepaymentService.generateRepayments(bond, List.of(date), CALCULATION_DATE))
                .thenReturn(Arrays.asList(
                        null,
                        new PrincipalRepayment(date, new BigDecimal("10.00"), BigDecimal.ZERO),
                        null));

        CouponCalculationService couponCalculationService = mock(CouponCalculationService.class);
        when(couponCalculationService.calculateCoupons(
                bond,
                List.of(date),
                Arrays.asList(
                        null,
                        new PrincipalRepayment(date, new BigDecimal("10.00"), BigDecimal.ZERO),
                        null),
                CALCULATION_DATE)).thenReturn(Arrays.asList(
                        null,
                        new CouponPayment(date, new BigDecimal("3.50"), BigDecimal.ZERO),
                        null));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                new AccruedInterestServiceImpl(new CouponScheduleService(couponDateGenerator)));

        List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);
        System.out.println("Generated cash flows: " + flows);

        assertEquals(2, flows.size());
        assertEquals(List.of(LocalDate.of(2026, 9, 3), date), flows.stream().map(XirrCalculator.CashFlow::date).toList());
        assertEquals(new BigDecimal("13.50"), flows.get(1).amount());

        System.out.println("PASS: Null entries inside dependency results ignored safely.");
    }

    @Test
    void shouldRejectNullBond() {
        System.out.println("\n========== TEST: shouldRejectNullBond ==========");

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateCashFlows(
                        null,
                        CALCULATION_DATE));

        System.out.println("Exception message: " + exception.getMessage());

        assertEquals(
                "Bond cannot be null",
                exception.getMessage());

        System.out.println("PASS: Null bond rejected correctly.");
    }

    @Test
    void shouldRejectNullCalculationDate() {
        System.out.println("\n========== TEST: shouldRejectNullCalculationDate ==========");

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateCashFlows(
                        basicBond(),
                        null));

        System.out.println("Exception message: " + exception.getMessage());

        assertEquals(
                "Calculation date cannot be null",
                exception.getMessage());

        System.out.println("PASS: Null calculation date rejected correctly.");
    }

    @Test
    void shouldRejectMissingPrice() {
        System.out.println("\n========== TEST: shouldRejectMissingPrice ==========");

        Bond cashBond = basicBond();
        cashBond.setPrice(null);

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateCashFlows(
                        cashBond,
                        CALCULATION_DATE));

        System.out.println("Exception message: " + exception.getMessage());

        assertEquals(
                "Bond price is required",
                exception.getMessage());

        System.out.println("PASS: Missing bond price rejected correctly.");
    }

    @Test
    void shouldRejectNonPositivePrice() {
        System.out.println("\n========== TEST: shouldRejectNonPositivePrice ==========");

        Bond cashBond = basicBond();
        cashBond.setPrice(BigDecimal.ZERO);

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateCashFlows(
                        cashBond,
                        CALCULATION_DATE));

        System.out.println("Exception message: " + exception.getMessage());

        assertEquals(
                "Bond price must be positive",
                exception.getMessage());

        System.out.println("PASS: Non-positive bond price rejected correctly.");
    }

    @Test
    void shouldRejectNegativePrice() {
        System.out.println("\n========== TEST: shouldRejectNegativePrice ==========");

        Bond cashBond = basicBond();
        cashBond.setPrice(new BigDecimal("-10.00"));

        BondCashFlowServiceImpl service = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateCashFlows(cashBond, CALCULATION_DATE));

        assertEquals("Bond price must be positive", exception.getMessage());
        System.out.println("PASS: Negative bond price rejected correctly.");
    }

    private Bond basicBond() {
        Bond bond = new Bond();
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setPrice(new BigDecimal("94.50"));
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setMaturityType(MaturityType.FIXED);
        return bond;
    }

    private Bond fullBond() {
        Bond bond = basicBond();
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
        return bond;
    }
}
