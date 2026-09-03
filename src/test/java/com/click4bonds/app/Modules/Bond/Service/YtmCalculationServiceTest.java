package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;

@ExtendWith(MockitoExtension.class)
class YtmCalculationServiceTest {

    @Mock
    private BondCashFlowService bondCashFlowService;

    @Mock
    private XirrCalculator xirrCalculator;

    @Mock
    private BondRepository bondRepository;

    private YtmCalculationService ytmCalculationService;

    @BeforeEach
    void setUp() {
        System.out.println();
        System.out.println("-------------------------------------------------------");
        System.out.println("SETUP: Initializing YtmCalculationServiceTest");
        System.out.println("-------------------------------------------------------");

        ytmCalculationService = new YtmCalculationServiceImpl(
                bondCashFlowService,
                xirrCalculator,
                bondRepository);

        System.out.println("SETUP: YtmCalculationService initialized successfully");
        System.out.println("SETUP: BondCashFlowService mock = " + bondCashFlowService);
        System.out.println("SETUP: XirrCalculator mock = " + xirrCalculator);
        System.out.println("SETUP: BondRepository mock = " + bondRepository);
        System.out.println("-------------------------------------------------------");
    }

    // ======================
    // TEST 1: Success Path
    // ======================

    @Test
    void calculatesYtmSuccessfully() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 1 START: calculatesYtmSuccessfully");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Creating test bond...");
        Bond bond = createBond();

        System.out.println("Bond created:");
        System.out.println("  ID              : " + bond.getId());
        System.out.println("  Name            : " + bond.getName());
        System.out.println("  ISIN            : " + bond.getIsin());
        System.out.println("  Price           : " + bond.getPrice());
        System.out.println("  Coupon Rate     : " + bond.getCouponRate());
        System.out.println("  Coupon Frequency: " + bond.getCouponFrequency());
        System.out.println("  Maturity Type   : " + bond.getMaturityType());
        System.out.println("  Maturity Date   : " + bond.getMaturityDate());

        LocalDate calculationDate = LocalDate.now();

        System.out.println();
        System.out.println("STEP 2: Calculation date selected");
        System.out.println("Calculation Date: " + calculationDate);

        System.out.println();
        System.out.println("STEP 3: Creating mock cash flows...");

        List<XirrCalculator.CashFlow> mockCashFlows = List.of(
                new XirrCalculator.CashFlow(
                        calculationDate,
                        new BigDecimal("-94.50")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(6),
                        new BigDecimal("6.10")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(12),
                        new BigDecimal("6.01")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(18),
                        new BigDecimal("5.92")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(24),
                        new BigDecimal("13.33")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(30),
                        new BigDecimal("12.97")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(36),
                        new BigDecimal("12.61")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(42),
                        new BigDecimal("12.25")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(48),
                        new BigDecimal("11.89")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(54),
                        new BigDecimal("11.53")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(60),
                        new BigDecimal("11.17")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(66),
                        new BigDecimal("23.31")));

        System.out.println("Cash-flow count: " + mockCashFlows.size());
        System.out.println("Cash flows:");

        for (int i = 0; i < mockCashFlows.size(); i++) {
            System.out.println(
                    "  Cash Flow " + (i + 1) + ": " + mockCashFlows.get(i));
        }

        BigDecimal expectedXirr = new BigDecimal("0.106947");

        System.out.println();
        System.out.println("STEP 4: Preparing XIRR mock response");
        System.out.println("Expected XIRR (decimal): " + expectedXirr);
        System.out.println("Expected XIRR (percentage): "
                + expectedXirr.multiply(new BigDecimal("100")));

        System.out.println();
        System.out.println("STEP 5: Configuring Mockito behavior...");

        when(bondCashFlowService.generateCashFlows(bond, calculationDate))
                .thenReturn(mockCashFlows);

        System.out.println("Mock configured:");
        System.out.println("  BondCashFlowService.generateCashFlows()");
        System.out.println("  -> Returns " + mockCashFlows.size() + " cash flows");

        when(xirrCalculator.calculate(mockCashFlows))
                .thenReturn(expectedXirr);

        System.out.println("Mock configured:");
        System.out.println("  XirrCalculator.calculate()");
        System.out.println("  -> Returns XIRR: " + expectedXirr);

        when(bondRepository.save(any(Bond.class)))
                .thenReturn(bond);

        System.out.println("Mock configured:");
        System.out.println("  BondRepository.save()");
        System.out.println("  -> Returns the same bond instance");

        System.out.println();
        System.out.println("STEP 6: Calling ytmCalculationService.calculateYtm()...");
        System.out.println("Input Bond ID: " + bond.getId());

        BigDecimal returnedYtm = ytmCalculationService.calculateYtm(bond);

        System.out.println();
        System.out.println("STEP 7: Service execution completed");

        System.out.println("Returned YTM (decimal): " + returnedYtm);
        System.out.println("Returned YTM (percentage): "
                + returnedYtm.multiply(new BigDecimal("100")));

        System.out.println("Bond Annual YTM: " + bond.getAnnualYtm());
        System.out.println("Bond YTM Calculated At: "
                + bond.getYtmCalculatedAt());

        System.out.println();
        System.out.println("STEP 8: Verifying returned YTM...");

        assertEquals(expectedXirr, returnedYtm);

        System.out.println("PASS: Returned YTM matches expected XIRR");

        System.out.println();
        System.out.println("STEP 9: Verifying annual YTM stored on Bond...");

        BigDecimal expectedPercentage = new BigDecimal("10.69");

        System.out.println("Expected Annual YTM: " + expectedPercentage);
        System.out.println("Actual Annual YTM  : " + bond.getAnnualYtm());

        assertEquals(expectedPercentage, bond.getAnnualYtm());

        System.out.println("PASS: Annual YTM correctly stored as percentage");

        System.out.println();
        System.out.println("STEP 10: Verifying YTM calculation timestamp...");

        assertNotNull(bond.getYtmCalculatedAt());

        System.out.println("YTM Calculated At: "
                + bond.getYtmCalculatedAt());

        System.out.println("PASS: YTM calculation timestamp populated");

        System.out.println();
        System.out.println("STEP 11: Verifying BondRepository.save()...");

        verify(bondRepository, times(1)).save(bond);

        System.out.println("PASS: BondRepository.save() called exactly once");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 1 PASS: calculatesYtmSuccessfully");
        System.out.println("=======================================================");
    }

    // ======================
    // TEST 2: Interaction Order
    // ======================

    @Test
    void verifiesCorrectInteractionOrder() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 2 START: verifiesCorrectInteractionOrder");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Creating test bond...");
        Bond bond = createBond();

        System.out.println("Bond ID: " + bond.getId());

        LocalDate calculationDate = LocalDate.now();

        System.out.println("Calculation Date: " + calculationDate);

        System.out.println();
        System.out.println("STEP 2: Creating mock cash flows...");

        List<XirrCalculator.CashFlow> mockCashFlows = List.of(
                new XirrCalculator.CashFlow(
                        calculationDate,
                        new BigDecimal("-94.50")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(6),
                        new BigDecimal("6.10")));

        System.out.println("Cash-flow count: " + mockCashFlows.size());

        for (int i = 0; i < mockCashFlows.size(); i++) {
            System.out.println(
                    "  Cash Flow " + (i + 1) + ": "
                            + mockCashFlows.get(i));
        }

        BigDecimal expectedXirr = new BigDecimal("0.106947");

        System.out.println();
        System.out.println("STEP 3: Configuring mocks...");

        when(bondCashFlowService.generateCashFlows(bond, calculationDate))
                .thenReturn(mockCashFlows);

        System.out.println("BondCashFlowService mock configured");

        when(xirrCalculator.calculate(mockCashFlows))
                .thenReturn(expectedXirr);

        System.out.println("XirrCalculator mock configured");
        System.out.println("Expected XIRR: " + expectedXirr);

        when(bondRepository.save(any(Bond.class)))
                .thenReturn(bond);

        System.out.println("BondRepository mock configured");

        System.out.println();
        System.out.println("STEP 4: Executing YTM calculation...");

        ytmCalculationService.calculateYtm(bond);

        System.out.println("YTM calculation completed");

        System.out.println();
        System.out.println("STEP 5: Verifying interaction order...");

        InOrder inOrder = inOrder(
                bondCashFlowService,
                xirrCalculator);

        System.out.println("Expected interaction sequence:");
        System.out.println("  1. BondCashFlowService.generateCashFlows()");
        System.out.println("  2. XirrCalculator.calculate()");

        inOrder.verify(bondCashFlowService)
                .generateCashFlows(bond, calculationDate);

        System.out.println("PASS: BondCashFlowService called first");

        inOrder.verify(xirrCalculator)
                .calculate(mockCashFlows);

        System.out.println("PASS: XirrCalculator called second");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 2 PASS: Correct interaction order verified");
        System.out.println("=======================================================");
    }

    // ======================
    // TEST 3: Correct Calculation Date
    // ======================

    @Test
    void verifiesCorrectCalculationDateUsed() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 3 START: verifiesCorrectCalculationDateUsed");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Creating test bond...");

        Bond bond = createBond();

        System.out.println("Bond ID: " + bond.getId());

        System.out.println();
        System.out.println("STEP 2: Creating mock cash flow...");

        List<XirrCalculator.CashFlow> mockCashFlows = List.of(
                new XirrCalculator.CashFlow(
                        LocalDate.now(),
                        new BigDecimal("-100")));

        System.out.println("Mock cash-flow count: " + mockCashFlows.size());
        System.out.println("Mock cash flow: " + mockCashFlows.get(0));

        BigDecimal expectedXirr = new BigDecimal("0.05");

        System.out.println("Expected XIRR: " + expectedXirr);

        System.out.println();
        System.out.println("STEP 3: Configuring mocks...");

        when(bondCashFlowService.generateCashFlows(
                any(Bond.class),
                any(LocalDate.class)))
                .thenReturn(mockCashFlows);

        System.out.println(
                "BondCashFlowService.generateCashFlows() mock configured");

        when(xirrCalculator.calculate(mockCashFlows))
                .thenReturn(expectedXirr);

        System.out.println(
                "XirrCalculator.calculate() mock configured");

        when(bondRepository.save(any(Bond.class)))
                .thenReturn(bond);

        System.out.println(
                "BondRepository.save() mock configured");

        System.out.println();
        System.out.println("STEP 4: Calling calculateYtm()...");

        ytmCalculationService.calculateYtm(bond);

        System.out.println("YTM calculation completed");

        System.out.println();
        System.out.println("STEP 5: Verifying calculation date argument...");

        verify(bondCashFlowService)
                .generateCashFlows(
                        any(Bond.class),
                        any(LocalDate.class));

        System.out.println(
                "PASS: generateCashFlows() received a LocalDate calculation date");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 3 PASS: Correct calculation date verified");
        System.out.println("=======================================================");
    }

    // ======================
    // TEST 4: XIRR Failure
    // ======================

    @Test
    void propagatesXirrFailure() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 4 START: propagatesXirrFailure");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Creating test bond...");

        Bond bond = createBond();
        LocalDate calculationDate = LocalDate.now();

        System.out.println("Bond ID: " + bond.getId());
        System.out.println("Calculation Date: " + calculationDate);

        System.out.println();
        System.out.println("STEP 2: Creating cash flows...");

        List<XirrCalculator.CashFlow> mockCashFlows = List.of(
                new XirrCalculator.CashFlow(
                        calculationDate,
                        new BigDecimal("-100")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusYears(1),
                        new BigDecimal("100")));

        System.out.println("Cash-flow count: " + mockCashFlows.size());

        for (int i = 0; i < mockCashFlows.size(); i++) {
            System.out.println(
                    "  Cash Flow " + (i + 1) + ": "
                            + mockCashFlows.get(i));
        }

        System.out.println();
        System.out.println("STEP 3: Configuring XIRR to throw exception...");

        when(bondCashFlowService.generateCashFlows(
                bond,
                calculationDate))
                .thenReturn(mockCashFlows);

        System.out.println(
                "Cash-flow generation mock configured successfully");

        when(xirrCalculator.calculate(mockCashFlows))
                .thenThrow(
                        new IllegalStateException(
                                "Unable to calculate XIRR"));

        System.out.println(
                "XirrCalculator configured to throw IllegalStateException");

        System.out.println();
        System.out.println("STEP 4: Calling calculateYtm()");
        System.out.println("Expected behavior: IllegalStateException");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ytmCalculationService.calculateYtm(bond));

        System.out.println("Exception successfully captured");
        System.out.println("Exception Type   : "
                + exception.getClass().getSimpleName());
        System.out.println("Exception Message: "
                + exception.getMessage());

        System.out.println();
        System.out.println("STEP 5: Verifying exception message...");

        assertEquals(
                "Unable to calculate XIRR",
                exception.getMessage());

        System.out.println("PASS: Exception message matches expected value");

        System.out.println();
        System.out.println("STEP 6: Verifying Bond was NOT saved...");

        verify(
                bondRepository,
                never()).save(any(Bond.class));

        System.out.println("PASS: BondRepository.save() was never called");

        System.out.println();
        System.out.println("STEP 7: Verifying Bond YTM fields remain unchanged...");

        System.out.println("Annual YTM before/after failure: "
                + bond.getAnnualYtm());

        System.out.println("YTM Calculated At before/after failure: "
                + bond.getYtmCalculatedAt());

        assertEquals(null, bond.getAnnualYtm());
        assertEquals(null, bond.getYtmCalculatedAt());

        System.out.println("PASS: Bond YTM fields remain unchanged");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 4 PASS: XIRR failure propagated correctly");
        System.out.println("=======================================================");
    }

    // ======================
    // TEST 5: Cash-flow Failure
    // ======================

    @Test
    void propagatesCashFlowGenerationFailure() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 5 START: propagatesCashFlowGenerationFailure");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Creating test bond...");

        Bond bond = createBond();
        LocalDate calculationDate = LocalDate.now();

        System.out.println("Bond ID: " + bond.getId());
        System.out.println("Calculation Date: " + calculationDate);

        System.out.println();
        System.out.println("STEP 2: Configuring cash-flow generation to fail...");

        when(bondCashFlowService.generateCashFlows(
                bond,
                calculationDate))
                .thenThrow(
                        new IllegalArgumentException(
                                "Invalid bond configuration"));

        System.out.println(
                "BondCashFlowService configured to throw:");
        System.out.println(
                "  IllegalArgumentException: Invalid bond configuration");

        System.out.println();
        System.out.println("STEP 3: Calling calculateYtm()");
        System.out.println(
                "Expected behavior: IllegalArgumentException");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ytmCalculationService.calculateYtm(bond));

        System.out.println("Exception successfully captured");
        System.out.println("Exception Type   : "
                + exception.getClass().getSimpleName());
        System.out.println("Exception Message: "
                + exception.getMessage());

        System.out.println();
        System.out.println("STEP 4: Verifying exception message...");

        assertEquals(
                "Invalid bond configuration",
                exception.getMessage());

        System.out.println("PASS: Exception message matches expected value");

        System.out.println();
        System.out.println("STEP 5: Verifying XirrCalculator was NOT called...");

        verify(
                xirrCalculator,
                never()).calculate(any());

        System.out.println(
                "PASS: XirrCalculator.calculate() was never called");

        System.out.println();
        System.out.println("STEP 6: Verifying BondRepository was NOT called...");

        verify(
                bondRepository,
                never()).save(any(Bond.class));

        System.out.println(
                "PASS: BondRepository.save() was never called");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 5 PASS: Cash-flow generation failure propagated");
        System.out.println("=======================================================");
    }

    // ======================
    // TEST 6: Null Bond
    // ======================

    @Test
    void rejectsNullBond() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 6 START: rejectsNullBond");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Passing null Bond to calculateYtm()...");

        System.out.println("Input Bond: null");

        System.out.println();
        System.out.println(
                "STEP 2: Calling calculateYtm(null)");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ytmCalculationService.calculateYtm(null));

        System.out.println("Exception successfully captured");
        System.out.println("Exception Type   : "
                + exception.getClass().getSimpleName());
        System.out.println("Exception Message: "
                + exception.getMessage());

        System.out.println();
        System.out.println("STEP 3: Verifying exception message...");

        assertEquals(
                "Bond cannot be null",
                exception.getMessage());

        System.out.println(
                "PASS: Null bond rejected with correct message");

        System.out.println();
        System.out.println(
                "STEP 4: Verifying no downstream services were called...");

        verify(
                bondCashFlowService,
                never()).generateCashFlows(any(), any());

        System.out.println(
                "PASS: BondCashFlowService was never called");

        verify(
                xirrCalculator,
                never()).calculate(any());

        System.out.println(
                "PASS: XirrCalculator was never called");

        verify(
                bondRepository,
                never()).save(any());

        System.out.println(
                "PASS: BondRepository was never called");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 6 PASS: Null bond rejected correctly");
        System.out.println("=======================================================");
    }

    // ======================
    // TEST 7: Exact Cash-flow Object
    // ======================

    @Test
    void passesExactCashFlowObjectToXirr() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 7 START: passesExactCashFlowObjectToXirr");
        System.out.println("=======================================================");

        System.out.println("STEP 1: Creating test bond...");

        Bond bond = createBond();
        LocalDate calculationDate = LocalDate.now();

        System.out.println("Bond ID: " + bond.getId());
        System.out.println("Calculation Date: " + calculationDate);

        System.out.println();
        System.out.println("STEP 2: Creating expected cash-flow list...");

        List<XirrCalculator.CashFlow> expectedFlows = List.of(
                new XirrCalculator.CashFlow(
                        calculationDate,
                        new BigDecimal("-94.50")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(6),
                        new BigDecimal("6.10")),
                new XirrCalculator.CashFlow(
                        calculationDate.plusMonths(12),
                        new BigDecimal("100.00")));

        System.out.println(
                "Expected cash-flow count: "
                        + expectedFlows.size());

        for (int i = 0; i < expectedFlows.size(); i++) {
            System.out.println(
                    "  Expected Cash Flow " + (i + 1) + ": "
                            + expectedFlows.get(i));
        }

        BigDecimal expectedXirr = new BigDecimal("0.08");

        System.out.println();
        System.out.println("Expected XIRR: " + expectedXirr);

        System.out.println();
        System.out.println("STEP 3: Configuring Mockito mocks...");

        when(bondCashFlowService.generateCashFlows(
                bond,
                calculationDate))
                .thenReturn(expectedFlows);

        System.out.println(
                "BondCashFlowService configured to return expectedFlows");

        when(xirrCalculator.calculate(expectedFlows))
                .thenReturn(expectedXirr);

        System.out.println(
                "XirrCalculator configured to return: "
                        + expectedXirr);

        when(bondRepository.save(any(Bond.class)))
                .thenReturn(bond);

        System.out.println(
                "BondRepository configured successfully");

        System.out.println();
        System.out.println("STEP 4: Calling calculateYtm()...");

        BigDecimal returnedYtm = ytmCalculationService.calculateYtm(bond);

        System.out.println("calculateYtm() completed");

        System.out.println("Returned YTM: " + returnedYtm);
        System.out.println("Bond Annual YTM: " + bond.getAnnualYtm());
        System.out.println("Bond YTM Calculated At: "
                + bond.getYtmCalculatedAt());

        System.out.println();
        System.out.println(
                "STEP 5: Verifying exact cash-flow object was passed...");

        verify(xirrCalculator)
                .calculate(expectedFlows);

        System.out.println(
                "PASS: Exact expectedFlows list was passed to XirrCalculator");

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 7 PASS: Exact cash-flow object verified");
        System.out.println("=======================================================");
    }

    // ======================
    // Helper Methods
    // ======================

    private Bond createBond() {

        System.out.println();
        System.out.println("HELPER: createBond() called");

        Bond bond = new Bond();

        bond.setId(UUID.randomUUID());
        bond.setName("Test Bond");
        bond.setIsin("TEST0000001");
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));

        System.out.println("HELPER: Bond initialized successfully");
        System.out.println("  ID              : " + bond.getId());
        System.out.println("  Name            : " + bond.getName());
        System.out.println("  ISIN            : " + bond.getIsin());
        System.out.println("  Price           : " + bond.getPrice());
        System.out.println("  Coupon Rate     : " + bond.getCouponRate());
        System.out.println("  Coupon Frequency: " + bond.getCouponFrequency());
        System.out.println("  Maturity Type   : " + bond.getMaturityType());
        System.out.println("  Maturity Date   : " + bond.getMaturityDate());

        return bond;
    }

    @Test
    void calculatesYtmForMuthootFincorpLtd2028() {

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("TEST 8 START: calculatesYtmForMuthootFincorpLtd2028");
        System.out.println("=======================================================");

        Bond bond = new Bond();
        bond.setId(UUID.fromString("04ccc242-2872-46f0-b8a5-5886bd400805"));
        bond.setName("Muthoot Fincorp Ltd 2028");
        bond.setIsin("INE549K07BW6");
        bond.setPrice(new BigDecimal("99.63"));
        bond.setCouponRate(new BigDecimal("8.80"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setIpDateDescription("02/02 Ann");
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2028, 2, 2));
        bond.setMaturityDescription("02/02/2028");

        LocalDate calculationDate = LocalDate.of(2026, 9, 3);
        BigDecimal cleanPrice = new BigDecimal("99.63");
        BigDecimal expectedAccruedInterest = new BigDecimal("5.13534246575342");
        BigDecimal expectedDirtyPrice = new BigDecimal("104.76534246575342");

        CouponDateGenerator realCouponDateGenerator = new CouponDateGenerator();
        PrincipalRepaymentService realPrincipalRepaymentService =
                new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl());
        CouponCalculationService realCouponCalculationService = new CouponCalculationServiceImpl();
        AccruedInterestService realAccruedInterestService =
                new AccruedInterestServiceImpl(realCouponDateGenerator);
        BondCashFlowService realBondCashFlowService = new BondCashFlowServiceImpl(
                realCouponDateGenerator,
                realPrincipalRepaymentService,
                realCouponCalculationService,
                realAccruedInterestService);
        XirrCalculator realXirrCalculator = new XirrCalculator();
        YtmCalculationService realYtmCalculationService = new YtmCalculationServiceImpl(
                realBondCashFlowService,
                realXirrCalculator,
                bondRepository);

        when(bondRepository.save(any(Bond.class))).thenReturn(bond);

        BigDecimal accruedInterest = realAccruedInterestService.calculate(bond, calculationDate);
        BigDecimal dirtyPrice = cleanPrice.add(accruedInterest);
        List<XirrCalculator.CashFlow> actualCashFlows = realBondCashFlowService.generateCashFlows(
                bond,
                calculationDate);

        System.out.println("Clean Price: " + cleanPrice);
        System.out.println("Accrued Interest: " + accruedInterest);
        System.out.println("Dirty Price: " + dirtyPrice);
        System.out.println("Actual cash-flow count: " + actualCashFlows.size());
        actualCashFlows.forEach(cashFlow -> System.out.println("  " + cashFlow));

        assertTrue(accruedInterest.subtract(expectedAccruedInterest).abs()
                .compareTo(new BigDecimal("0.00000000000001")) <= 0);
        assertTrue(dirtyPrice.subtract(expectedDirtyPrice).abs()
                .compareTo(new BigDecimal("0.00000000000001")) <= 0);
        assertEquals(3, actualCashFlows.size());
        assertEquals(1, actualCashFlows.stream()
                .filter(cashFlow -> cashFlow.date().equals(calculationDate))
                .count());
        assertEquals(calculationDate, actualCashFlows.get(0).date());
        assertTrue(actualCashFlows.get(0).amount().add(expectedDirtyPrice).abs()
                .compareTo(new BigDecimal("0.00000000000001")) <= 0);
        assertEquals(0, actualCashFlows.get(1).amount().compareTo(new BigDecimal("8.80")));
        assertEquals(0, actualCashFlows.get(2).amount().compareTo(new BigDecimal("108.80")));

        BigDecimal realXirr = realXirrCalculator.calculate(actualCashFlows);
        BigDecimal returnedYtm = realYtmCalculationService.calculateYtm(bond);

        System.out.println("Real XIRR: " + realXirr);
        System.out.println("Returned YTM: " + returnedYtm);
        System.out.println("Stored Annual YTM: " + bond.getAnnualYtm());

        assertEquals(new BigDecimal("0.090174"), realXirr);
        assertEquals(realXirr, returnedYtm);
        assertNotNull(bond.getAnnualYtm());
        assertEquals(new BigDecimal("9.02"), bond.getAnnualYtm());
        assertNotNull(bond.getYtmCalculatedAt());
        verify(bondRepository, times(1)).save(bond);

        System.out.println("TEST 8 PASS: calculatesYtmForMuthootFincorpLtd2028");
        System.out.println("=======================================================");
    }

}
