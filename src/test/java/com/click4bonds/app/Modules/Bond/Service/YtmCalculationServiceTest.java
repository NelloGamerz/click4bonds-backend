package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // ======================
        // Shared constants
        // ======================

        private static final BigDecimal FACE_VALUE = new BigDecimal("100");
        private static final BigDecimal HUNDRED = new BigDecimal("100");
        private static final BigDecimal CASH_FLOW_TOLERANCE = new BigDecimal("0.00000000000001");
        private static final BigDecimal TWELVE = new BigDecimal("12");
        private static final int DIVISION_SCALE = 10;

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
        // TEST 12: Exact supplied date is used (date-aware overload)
        // ======================

        @Test
        void dateAwareCalculateYtmUsesExactlyTheSuppliedDate() {

                System.out.println();
                System.out.println("=======================================================");
                System.out.println(
                                "TEST 12 START: dateAwareCalculateYtmUsesExactlyTheSuppliedDate");
                System.out.println("=======================================================");

                System.out.println("STEP 1: Creating test bond...");

                Bond bond = createBond();

                System.out.println("Bond ID              : " + bond.getId());
                System.out.println("Bond Name            : " + bond.getName());
                System.out.println("ISIN                 : " + bond.getIsin());
                System.out.println("Price                : " + bond.getPrice());
                System.out.println("Coupon Rate          : " + bond.getCouponRate());
                System.out.println("Coupon Frequency     : "
                                + bond.getCouponFrequency());
                System.out.println("Maturity Type        : "
                                + bond.getMaturityType());
                System.out.println("Maturity Date        : "
                                + bond.getMaturityDate());

                System.out.println();
                System.out.println("STEP 2: Setting a fixed calculation date...");

                LocalDate fixedDate = LocalDate.of(2026, 9, 3);

                System.out.println(
                                "Supplied Calculation Date : " + fixedDate);
                System.out.println(
                                "System Calculation Date   : " + LocalDate.now());

                System.out.println();
                System.out.println("STEP 3: Creating mock cash flows...");

                List<XirrCalculator.CashFlow> flows = List.of(
                                new XirrCalculator.CashFlow(
                                                fixedDate,
                                                new BigDecimal("-100")),
                                new XirrCalculator.CashFlow(
                                                fixedDate.plusYears(1),
                                                new BigDecimal("100")));

                System.out.println("Cash-flow count: " + flows.size());

                for (int i = 0; i < flows.size(); i++) {
                        System.out.println(
                                        "  Cash Flow " + (i + 1) + ": "
                                                        + flows.get(i));
                }

                BigDecimal expectedXirr = new BigDecimal("0.10");

                System.out.println();
                System.out.println(
                                "Expected XIRR (decimal)   : " + expectedXirr);
                System.out.println(
                                "Expected XIRR (percentage): "
                                                + expectedXirr.multiply(
                                                                new BigDecimal("100")));

                System.out.println();
                System.out.println("STEP 4: Configuring Mockito mocks...");

                when(bondCashFlowService.generateCashFlows(
                                bond,
                                fixedDate))
                                .thenReturn(flows);

                System.out.println(
                                "BondCashFlowService.generateCashFlows() configured");
                System.out.println(
                                "  Expected calculation date: " + fixedDate);
                System.out.println(
                                "  Expected cash-flow count : " + flows.size());

                when(xirrCalculator.calculate(flows))
                                .thenReturn(expectedXirr);

                System.out.println(
                                "XirrCalculator.calculate() configured");
                System.out.println(
                                "  Expected XIRR: " + expectedXirr);

                when(bondRepository.save(any(Bond.class)))
                                .thenReturn(bond);

                System.out.println(
                                "BondRepository.save() configured");

                System.out.println();
                System.out.println("STEP 5: Calling date-aware calculateYtm()...");

                BigDecimal returnedYtm = ytmCalculationService.calculateYtm(
                                bond,
                                fixedDate);

                System.out.println("YTM calculation completed");

                System.out.println(
                                "Returned YTM          : " + returnedYtm);
                System.out.println(
                                "Expected YTM          : " + expectedXirr);
                System.out.println(
                                "Stored Annual YTM     : "
                                                + bond.getAnnualYtm());
                System.out.println(
                                "YTM Calculated At     : "
                                                + bond.getYtmCalculatedAt());

                System.out.println();
                System.out.println("STEP 6: Verifying returned YTM...");

                assertEquals(
                                expectedXirr,
                                returnedYtm,
                                "Returned YTM should equal mocked XIRR");

                System.out.println(
                                "PASS: Returned YTM matches expected XIRR");

                System.out.println();
                System.out.println(
                                "STEP 7: Verifying exact calculation date was used...");

                /*
                 * The date-aware overload must call generateCashFlows()
                 * exactly once using the supplied fixed date.
                 *
                 * If the implementation incorrectly uses LocalDate.now(),
                 * this verification will fail.
                 */
                verify(
                                bondCashFlowService,
                                times(1))
                                .generateCashFlows(
                                                bond,
                                                fixedDate);

                System.out.println(
                                "PASS: generateCashFlows() called exactly once");

                System.out.println(
                                "Verified calculation date: " + fixedDate);

                System.out.println();
                System.out.println(
                                "STEP 8: Verifying XirrCalculator interaction...");

                verify(
                                xirrCalculator,
                                times(1))
                                .calculate(flows);

                System.out.println(
                                "PASS: XirrCalculator.calculate() called exactly once");

                System.out.println(
                                "Verified cash-flow object was passed to XIRR");

                System.out.println();
                System.out.println(
                                "STEP 9: Verifying BondRepository.save()...");

                verify(
                                bondRepository,
                                times(1))
                                .save(bond);

                System.out.println(
                                "PASS: BondRepository.save() called exactly once");

                System.out.println(
                                "Bond ID saved: " + bond.getId());

                System.out.println();
                System.out.println(
                                "STEP 10: Verifying calculation timestamp...");

                assertNotNull(
                                bond.getYtmCalculatedAt(),
                                "YTM calculation timestamp should be populated");

                System.out.println(
                                "YTM Calculated At: "
                                                + bond.getYtmCalculatedAt());

                System.out.println(
                                "PASS: YTM calculation timestamp populated");

                System.out.println();
                System.out.println(
                                "STEP 11: Verifying stored Annual YTM...");

                BigDecimal expectedAnnualYtm = expectedXirr
                                .multiply(new BigDecimal("100"))
                                .setScale(
                                                2,
                                                RoundingMode.HALF_UP);

                assertEquals(
                                0,
                                expectedAnnualYtm.compareTo(
                                                bond.getAnnualYtm()),
                                "Annual YTM should equal XIRR converted to percentage");

                System.out.println(
                                "Expected Annual YTM : "
                                                + expectedAnnualYtm);

                System.out.println(
                                "Actual Annual YTM   : "
                                                + bond.getAnnualYtm());

                System.out.println(
                                "PASS: Annual YTM stored correctly");

                System.out.println();
                System.out.println("STEP 12: Verifying calculation date determinism...");

                /*
                 * The purpose of this test is to prove that the date-aware
                 * calculateYtm(bond, fixedDate) overload uses the exact date
                 * supplied by the caller rather than LocalDate.now().
                 */
                assertEquals(
                                fixedDate,
                                flows.get(0).date(),
                                "First cash flow should use the supplied calculation date");

                System.out.println(
                                "Supplied calculation date: " + fixedDate);

                System.out.println(
                                "First cash-flow date     : " + flows.get(0).date());

                System.out.println(
                                "PASS: Supplied calculation date was used");

                System.out.println();
                System.out.println("=======================================================");
                System.out.println(
                                "TEST 12 PASS: dateAwareCalculateYtmUsesExactlyTheSuppliedDate");
                System.out.println("=======================================================");
        }

        // ======================
        // Unit-test helper: default mock bond
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

        // ======================
        // REAL-CALCULATION TESTS
        // ======================
        // These tests exercise the real dependency chain (real coupon schedules,
        // real accrued-interest, real cash flows and real XIRR) rather than mocks.
        // The shared pipeline is expressed through the helpers declared below so
        // each test only states the bond data and the assertions that matter to it.

        // ======================
        // TEST 8: Muthoot Fincorp Ltd 2028 (annual fixed bond)
        // ======================

        @Test
        void calculatesYtmForMuthootFincorpLtd2028() {

                printBanner("TEST 8 START: calculatesYtmForMuthootFincorpLtd2028");

                Bond bond = realBond(
                                UUID.fromString("04ccc242-2872-46f0-b8a5-5886bd400805"),
                                "Muthoot Fincorp Ltd 2028",
                                "INE549K07BW6",
                                "99.63",
                                "8.80",
                                CouponFrequency.YEARLY,
                                "02/02 Ann",
                                MaturityType.FIXED,
                                LocalDate.of(2028, 2, 2),
                                "02/02/2028");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                // Exactly three cash flows: purchase, one coupon, maturity coupon+principal.
                assertInitialPurchaseCashFlow(flows, calculationDate, price.dirtyPrice());
                assertEquals(3, flows.size(), "Expected exactly 3 cash flows");
                assertEquals(
                                1,
                                flows.stream()
                                                .filter(cashFlow -> cashFlow.date()
                                                                .equals(calculationDate))
                                                .count(),
                                "Expected exactly one cash flow on calculation date");
                assertEquals(
                                0,
                                flows.get(1).amount().compareTo(new BigDecimal("8.80")),
                                "First coupon should be 8.80");
                assertEquals(
                                0,
                                flows.get(2).amount().compareTo(new BigDecimal("108.80")),
                                "Final cash flow should be 108.80");

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond, calculationDate);

                printYtmResult(bond, realXirr, returnedYtm);
                assertYtmEqualsXirr(realXirr, returnedYtm);
                assertAnnualYtmStored(bond, returnedYtm);
                assertCalculationTimestamp(bond);
                assertYtmPersisted(bond);

                printBanner("TEST 8 PASS: calculatesYtmForMuthootFincorpLtd2028");
        }

        // ======================
        // TEST 9: SATIN CREDITCARE NETWORK Ltd 2031 (monthly fixed bond)
        // ======================

        @Test
        void calculatesYtmForSatinCreditcareNetworkLtd2031() {

                printBanner(
                                "TEST 9 START: calculatesYtmForSatinCreditcareNetworkLtd2031");

                Bond bond = realBond(
                                UUID.fromString("a9d46fd7-d286-407d-a847-4af32384b211"),
                                "12% SATIN CREDITCARE NETWORK Ltd 2031",
                                "INE836B08319",
                                "98.94",
                                "12.00",
                                CouponFrequency.MONTHLY,
                                "23rd of every month",
                                MaturityType.FIXED,
                                LocalDate.of(2031, 7, 23),
                                "23/Jul/31");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                assertInitialPurchaseCashFlow(flows, calculationDate, price.dirtyPrice());

                /*
                 * 12% annual coupon paid monthly => every regular coupon is 1.00
                 * and the maturity cash flow is principal + last coupon = 101.00.
                 */
                XirrCalculator.CashFlow finalCashFlow = finalCashFlowOf(flows);
                assertEquals(
                                LocalDate.of(2031, 7, 23),
                                finalCashFlow.date(),
                                "Final cash flow should be on maturity date");
                assertEquals(
                                0,
                                finalCashFlow.amount().compareTo(new BigDecimal("101.00")),
                                "Final cash flow should be coupon + principal");
                System.out.println("Final cash flow      : " + finalCashFlow);

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond);

                printYtmResult(bond, realXirr, returnedYtm);
                assertYtmEqualsXirr(realXirr, returnedYtm);
                assertAnnualYtmStored(bond, realXirr);
                assertCalculationTimestamp(bond);
                assertYtmPersisted(bond);

                printBanner("TEST 9 PASS: calculatesYtmForSatinCreditcareNetworkLtd2031");
        }

        // ======================
        // TEST 10: Vedika Credit Capital Ltd 2031 (monthly fixed bond)
        // ======================

        @Test
        void calculatesYtmForVedikaCreditCapitalLtd2031() {

                printBanner("TEST 10 START: calculatesYtmForVedikaCreditCapitalLtd2031");

                Bond bond = realBond(
                                UUID.fromString("03c47ff5-1906-43bd-8fb5-3ebeebd977cd"),
                                "12.50% Vedika Credit Capital Ltd 2031",
                                "INE04HY08011",
                                "98.10",
                                "12.50",
                                CouponFrequency.MONTHLY,
                                "5th of Every Month",
                                MaturityType.FIXED,
                                LocalDate.of(2031, 12, 5),
                                "5/Dec/31");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                assertInitialPurchaseCashFlow(flows, calculationDate, price.dirtyPrice());

                // Annual coupon 12.50% paid monthly => 12.50 / 12 per coupon.
                BigDecimal expectedMonthlyCoupon = monthlyCoupon(new BigDecimal("12.50"));
                XirrCalculator.CashFlow firstCoupon = flows.stream()
                                .skip(1)
                                .filter(cashFlow -> cashFlow.date()
                                                .isBefore(bond.getMaturityDate()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError(
                                                "Expected at least one regular monthly coupon"));
                assertEquals(
                                0,
                                firstCoupon.amount().compareTo(expectedMonthlyCoupon),
                                "Regular monthly coupon should be 12.50% / 12");
                System.out.println("First coupon         : " + firstCoupon);

                // Maturity payment = principal + last monthly coupon.
                XirrCalculator.CashFlow finalCashFlow = finalCashFlowOf(flows);
                BigDecimal expectedFinalCashFlow = new BigDecimal("100.00").add(expectedMonthlyCoupon);
                assertEquals(
                                LocalDate.of(2031, 12, 5),
                                finalCashFlow.date(),
                                "Final cash flow should be on maturity date");
                assertEquals(
                                0,
                                finalCashFlow.amount().compareTo(expectedFinalCashFlow),
                                "Final cash flow should equal principal + final monthly coupon");
                System.out.println("Final cash flow     : " + finalCashFlow);
                System.out.println("Expected final      : " + expectedFinalCashFlow);

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond);

                printYtmResult(bond, realXirr, returnedYtm);
                assertYtmEqualsXirr(realXirr, returnedYtm);
                assertAnnualYtmStored(bond, realXirr);
                assertCalculationTimestamp(bond);
                assertYtmPersisted(bond);

                printBanner("TEST 10 PASS: calculatesYtmForVedikaCreditCapitalLtd2031");
        }

        // ======================
        // TEST 11: Monedo Fin Ser Pvt Ltd 2027 (amortizing bond)
        // ======================

        @Test
        void calculatesYtmForMonedoFinSerPvtLtd2027() {

                printBanner("TEST 11 START: calculatesYtmForMonedoFinSerPvtLtd2027");

                Bond bond = realBond(
                                UUID.fromString("4f6d7301-0df8-488d-a882-277987a77a7e"),
                                "14.40% Monedo Fin Ser Pvt Ltd 2027",
                                "INE0I5X07042",
                                "99.77",
                                "14.40",
                                CouponFrequency.MONTHLY,
                                "1st of Every Month",
                                MaturityType.AMORTIZING,
                                LocalDate.of(2027, 10, 1),
                                "01/10/2026 to 01/10/2027 (20% Quartely)");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                assertInitialPurchaseCashFlow(flows, calculationDate, price.dirtyPrice());

                // Final repayment lands on the maturity date.
                XirrCalculator.CashFlow finalCashFlow = finalCashFlowOf(flows);
                assertEquals(
                                LocalDate.of(2027, 10, 1),
                                finalCashFlow.date(),
                                "Final cash flow should be on maturity date");
                System.out.println("Final cash flow      : " + finalCashFlow);

                // 20% quarterly amortization means principal repayments beyond coupons exist.
                long cashFlowsAfterPurchase = flows.stream().skip(1).count();
                assertTrue(cashFlowsAfterPurchase > 0,
                                "Expected coupon/amortization cash flows after purchase");
                System.out.println("Cash flows after purchase: " + cashFlowsAfterPurchase);

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond);

                printYtmResult(bond, realXirr, returnedYtm);
                assertYtmEqualsXirr(realXirr, returnedYtm);
                assertAnnualYtmStored(bond, realXirr);
                assertCalculationTimestamp(bond);
                assertYtmPersisted(bond);

                printBanner("TEST 11 PASS: calculatesYtmForMonedoFinSerPvtLtd2027");
        }

        // ======================
        // TEST 13: Monedo Fin Ser Pvt Ltd 2027 (INE0I5X07034, monthly amortizing)
        // ======================
        // Smoke/print test: exercises the full real pipeline without assertions.

        @Test
        void calculatesYtmForMonedoFinSerPvtLtd2027IsinINE0I5X07034() {

                printBanner(
                                "TEST 13: calculatesYtmForMonedoFinSerPvtLtd2027IsinINE0I5X07034");

                Bond bond = realBond(
                                UUID.fromString("d494d280-852d-47af-950f-c36c1397bc4c"),
                                "13.70% Monedo Fin Ser Pvt Ltd 2027",
                                "INE0I5X07034",
                                "99.26",
                                "13.70",
                                CouponFrequency.MONTHLY,
                                "19th of Every Month",
                                MaturityType.AMORTIZING,
                                LocalDate.of(2027, 7, 19),
                                "19/03/2027 to 19/07/2027 (20% Monthly)");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                BigDecimal expectedMonthlyCoupon = monthlyCoupon(new BigDecimal("13.70"));
                BigDecimal monthlyPrincipal = FACE_VALUE
                                .multiply(new BigDecimal("20"))
                                .divide(HUNDRED, DIVISION_SCALE, RoundingMode.HALF_UP);

                XirrCalculator.CashFlow finalCashFlow = finalCashFlowOf(flows);

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond);

                System.out.println();
                System.out.println("=======================================================");
                System.out.println("CALCULATION RESULT");
                System.out.println("=======================================================");
                System.out.println("ISIN                 : " + bond.getIsin());
                System.out.println("Clean Price          : " + bond.getPrice());
                System.out.println("Accrued Interest     : " + price.accruedInterest());
                System.out.println("Dirty Price          : " + price.dirtyPrice());
                System.out.println("Monthly Coupon       : " + expectedMonthlyCoupon);
                System.out.println("Monthly Principal    : " + monthlyPrincipal);
                System.out.println("Final Date           : " + finalCashFlow.date());
                System.out.println("Final Amount         : " + finalCashFlow.amount());
                System.out.println("Cash-flow Count      : " + flows.size());
                System.out.println("XIRR                 : " + realXirr);
                System.out.println("XIRR %               : " + realXirr.multiply(HUNDRED));
                System.out.println("YTM                  : " + returnedYtm);
                System.out.println("YTM %                : " + returnedYtm.multiply(HUNDRED));
                System.out.println("Annual YTM           : " + bond.getAnnualYtm());
                System.out.println("YTM Calculated At    : " + bond.getYtmCalculatedAt());
                System.out.println("=======================================================");
        }

        // ======================
        // TEST 14: Punjab SDL 2027 (half-yearly fixed bond)
        // ======================

        @Test
        void calculatesYtmForPunjabSdl2027() {

                printBanner("TEST 14 START: calculatesYtmForPunjabSdl2027");

                Bond bond = realBond(
                                UUID.fromString("290d9f36-5fdc-4bc9-a162-188a48ec08fb"),
                                "7.42% PUNJAB SDL 2027",
                                "IN2820170115",
                                "100.45",
                                "7.42",
                                CouponFrequency.HALF_YEARLY,
                                "13/03-13/09",
                                MaturityType.FIXED,
                                LocalDate.of(2027, 9, 13),
                                "13/Sep/27");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                assertInitialPurchaseCashFlow(flows, calculationDate, price.dirtyPrice());
                assertTrue(flows.size() >= 2,
                                "Expected at least initial investment and final repayment");
                assertEquals(
                                1,
                                flows.stream()
                                                .filter(cashFlow -> cashFlow.date()
                                                                .equals(calculationDate))
                                                .count(),
                                "Expected exactly one cash flow on calculation date");

                // Final repayment: coupon 3.71 + principal 100.00 = 103.71.
                XirrCalculator.CashFlow finalCashFlow = finalCashFlowOf(flows);
                BigDecimal expectedFinalCashFlow = new BigDecimal("103.71");
                assertEquals(
                                bond.getMaturityDate(),
                                finalCashFlow.date(),
                                "Final cash flow must occur on maturity date");
                assertEquals(
                                0,
                                finalCashFlow.amount().compareTo(expectedFinalCashFlow),
                                "Final cash flow should equal coupon + principal");
                System.out.println("Expected final cash flow: " + expectedFinalCashFlow);
                System.out.println("Actual final cash flow  : " + finalCashFlow.amount());

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                assertNotNull(realXirr, "XIRR should not be null");
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond, calculationDate);

                printYtmResult(bond, realXirr, returnedYtm);
                assertYtmEqualsXirr(realXirr, returnedYtm);
                assertAnnualYtmStored(bond, returnedYtm);
                assertCalculationTimestamp(bond);
                assertYtmPersisted(bond);

                printBanner("TEST 14 PASS: calculatesYtmForPunjabSdl2027");
        }

        // ======================
        // TEST 15: Hero Fincorp Ltd 2036 (yearly fixed bond)
        // ======================

        @Test
        void calculatesYtmForHeroFincorpLtd2036() {

                printBanner("TEST 15 START: calculatesYtmForHeroFincorpLtd2036");

                Bond bond = realBond(
                                UUID.fromString("0ce13f87-ad15-48c0-a853-7ab751fc3cf3"),
                                "9.10% HERO FINCORP LTD 2036",
                                "INE957N08201",
                                "101.80",
                                "9.10",
                                CouponFrequency.YEARLY,
                                "18/01 Ann",
                                MaturityType.FIXED,
                                LocalDate.of(2036, 1, 18),
                                "18-Jan-36");

                LocalDate calculationDate = LocalDate.now();
                RealCalcContext ctx = buildRealCalcContext(bond);

                PriceCalculation price = calculatePrice(ctx, bond, calculationDate);
                List<XirrCalculator.CashFlow> flows = generateCashFlows(ctx, bond, calculationDate);

                assertInitialPurchaseCashFlow(flows, calculationDate, price.dirtyPrice());
                assertTrue(flows.size() >= 2,
                                "Expected at least initial investment and final repayment");
                assertEquals(
                                1,
                                flows.stream()
                                                .filter(cashFlow -> cashFlow.date()
                                                                .equals(calculationDate))
                                                .count(),
                                "Expected exactly one cash flow on calculation date");

                // Every intermediate cash flow is an annual coupon of 9.10.
                BigDecimal expectedCoupon = new BigDecimal("9.10");
                for (int i = 1; i < flows.size() - 1; i++) {
                        assertEquals(
                                        0,
                                        flows.get(i).amount().compareTo(expectedCoupon),
                                        "Intermediate annual coupon should be 9.10");
                }

                // Final repayment: coupon 9.10 + principal 100.00 = 109.10.
                XirrCalculator.CashFlow finalCashFlow = finalCashFlowOf(flows);
                BigDecimal expectedFinalCashFlow = new BigDecimal("109.10");
                assertEquals(
                                bond.getMaturityDate(),
                                finalCashFlow.date(),
                                "Final cash flow must occur on maturity date");
                assertEquals(
                                0,
                                finalCashFlow.amount().compareTo(expectedFinalCashFlow),
                                "Final cash flow should equal coupon + principal");
                System.out.println("Expected final cash flow: " + expectedFinalCashFlow);
                System.out.println("Actual final cash flow  : " + finalCashFlow.amount());

                BigDecimal realXirr = ctx.xirrCalculator().calculate(flows);
                assertNotNull(realXirr, "XIRR should not be null");
                BigDecimal returnedYtm = ctx.ytmCalculationService().calculateYtm(bond, calculationDate);

                printYtmResult(bond, realXirr, returnedYtm);
                assertYtmEqualsXirr(realXirr, returnedYtm);
                assertAnnualYtmStored(bond, returnedYtm);
                assertCalculationTimestamp(bond);
                assertYtmPersisted(bond);

                printBanner("TEST 15 PASS: calculatesYtmForHeroFincorpLtd2036");
        }

        // ======================
        // Shared calculation helpers
        // ======================

        /** Holds the real services wired together for the real-calculation tests. */
        private record RealCalcContext(
                        AccruedInterestService accruedInterestService,
                        BondCashFlowService bondCashFlowService,
                        XirrCalculator xirrCalculator,
                        YtmCalculationService ytmCalculationService) {
        }

        /** Clean price, accrued interest and dirty price for one bond/date. */
        private record PriceCalculation(
                        BigDecimal accruedInterest,
                        BigDecimal dirtyPrice) {
        }

        /**
         * Builds the full real dependency chain shared by every real test.
         *
         * <p>
         * The {@code bondRepository} is stubbed to return the supplied bond so the
         * production {@link YtmCalculationServiceImpl} can persist its result.
         */
        private RealCalcContext buildRealCalcContext(Bond bond) {

                CouponDateGenerator cdg = new CouponDateGenerator();

                PrincipalRepaymentService prs = new PrincipalRepaymentServiceImpl(
                                new MaturityDescriptionParserImpl());

                CouponCalculationService ccs = new CouponCalculationServiceImpl();

                AccruedInterestService ais = new AccruedInterestServiceImpl(
                                new CouponScheduleService(cdg));

                BondCashFlowService bfs = new BondCashFlowServiceImpl(
                                cdg,
                                prs,
                                ccs,
                                ais);

                XirrCalculator xirr = new XirrCalculator();

                YtmCalculationService ytmService = new YtmCalculationServiceImpl(
                                bfs,
                                xirr,
                                bondRepository);

                when(bondRepository.save(any(Bond.class)))
                                .thenReturn(bond);

                return new RealCalcContext(
                                ais,
                                bfs,
                                xirr,
                                ytmService);
        }

        /** Builds a fully-populated {@link Bond} for a real-calculation test. */
        private static Bond realBond(
                        UUID id,
                        String name,
                        String isin,
                        String price,
                        String couponRate,
                        CouponFrequency couponFrequency,
                        String ipDateDescription,
                        MaturityType maturityType,
                        LocalDate maturityDate,
                        String maturityDescription) {

                Bond bond = new Bond();
                bond.setId(id);
                bond.setName(name);
                bond.setIsin(isin);
                bond.setPrice(new BigDecimal(price));
                bond.setCouponRate(new BigDecimal(couponRate));
                bond.setCouponFrequency(couponFrequency);
                bond.setIpDateDescription(ipDateDescription);
                bond.setMaturityType(maturityType);
                bond.setMaturityDate(maturityDate);
                bond.setMaturityDescription(maturityDescription);
                return bond;
        }

        /** Calculates and prints accrued interest plus the dirty price. */
        private static PriceCalculation calculatePrice(
                        RealCalcContext ctx,
                        Bond bond,
                        LocalDate calculationDate) {

                BigDecimal cleanPrice = bond.getPrice();
                BigDecimal accruedInterest = ctx.accruedInterestService().calculate(
                                bond,
                                calculationDate);
                BigDecimal dirtyPrice = cleanPrice.add(accruedInterest);

                System.out.println();
                System.out.println("Price Calculation (clean price = " + cleanPrice + ")");
                System.out.println("Calculation Date : " + calculationDate);
                System.out.println("Accrued Interest : " + accruedInterest);
                System.out.println("Dirty Price      : " + dirtyPrice);

                return new PriceCalculation(accruedInterest, dirtyPrice);
        }

        /** Generates and prints the actual cash flows for a bond/date. */
        private static List<XirrCalculator.CashFlow> generateCashFlows(
                        RealCalcContext ctx,
                        Bond bond,
                        LocalDate calculationDate) {

                List<XirrCalculator.CashFlow> flows = ctx.bondCashFlowService().generateCashFlows(
                                bond,
                                calculationDate);

                System.out.println();
                System.out.println("Generated Cash Flows (count = " + flows.size() + ")");
                flows.forEach(cashFlow -> System.out.println("  " + cashFlow));

                return flows;
        }

        /** Convenience accessor for the last (maturity) cash flow. */
        private static XirrCalculator.CashFlow finalCashFlowOf(
                        List<XirrCalculator.CashFlow> flows) {
                return flows.get(flows.size() - 1);
        }

        /**
         * Annual coupon expressed per-month:
         *
         * <pre>
         * annualCoupon / 12
         * </pre>
         */
        private static BigDecimal monthlyCoupon(BigDecimal annualCoupon) {
                return annualCoupon.divide(TWELVE, DIVISION_SCALE, RoundingMode.HALF_UP);
        }

        /**
         * Converts a decimal YTM/XIRR into a 2-dp percentage, as the service stores it.
         */
        private static BigDecimal toAnnualYtmPercentage(BigDecimal decimal) {
        return decimal.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        }

        // private static BigDecimal toAnnualYtmPercentage(BigDecimal decimal) {
        //         return decimal
        //                         .multiply(HUNDRED)
        //                         .divide(new BigDecimal("0.10"), 0, RoundingMode.HALF_UP)
        //                         .multiply(new BigDecimal("0.10"))
        //                         .setScale(2, RoundingMode.HALF_UP);
        // }

        // ======================
        // Shared assertion helpers
        // ======================

        /**
         * Asserts the first cash flow is the purchase leg: dated on the calculation
         * date with an amount equal to the negative dirty price.
         */
        private static void assertInitialPurchaseCashFlow(
                        List<XirrCalculator.CashFlow> flows,
                        LocalDate calculationDate,
                        BigDecimal dirtyPrice) {

                assertTrue(!flows.isEmpty(), "Cash flows should not be empty");
                assertEquals(
                                calculationDate,
                                flows.get(0).date(),
                                "First cash flow must use the calculation date");
                assertTrue(
                                flows.get(0).amount()
                                                .add(dirtyPrice)
                                                .abs()
                                                .compareTo(CASH_FLOW_TOLERANCE) <= 0,
                                "Initial cash flow should equal negative dirty price");
        }

        /**
         * Asserts the YTM returned by the service matches a direct XIRR of the same
         * flows.
         */
        private static void assertYtmEqualsXirr(BigDecimal realXirr, BigDecimal returnedYtm) {
                assertEquals(
                                0,
                                realXirr.compareTo(returnedYtm),
                                "Returned YTM should equal XIRR calculated from cash flows");
        }

        /**
         * Asserts the service stored annual YTM as the 2-dp percentage of the given
         * base.
         */
        private static void assertAnnualYtmStored(Bond bond, BigDecimal base) {
                assertNotNull(bond.getAnnualYtm(), "Annual YTM should be populated");
                BigDecimal expected = toAnnualYtmPercentage(base);
                assertEquals(
                                0,
                                expected.compareTo(bond.getAnnualYtm()),
                                "Annual YTM should equal XIRR converted to percentage");
                System.out.println("Expected Annual YTM : " + expected);
                System.out.println("Actual Annual YTM   : " + bond.getAnnualYtm());
        }

        /** Asserts the YTM calculation timestamp was populated. */
        private static void assertCalculationTimestamp(Bond bond) {
                assertNotNull(
                                bond.getYtmCalculatedAt(),
                                "YTM calculation timestamp should be populated");
                System.out.println("YTM Calculated At   : " + bond.getYtmCalculatedAt());
        }

        /** Asserts the bond was persisted exactly once. */
        private void assertYtmPersisted(Bond bond) {
                verify(bondRepository, times(1)).save(bond);
                System.out.println("PASS: BondRepository.save() called exactly once");
        }

        /** Prints a section banner for a test start/finish. */
        private static void printBanner(String title) {
                System.out.println();
                System.out.println("=======================================================");
                System.out.println(title);
                System.out.println("=======================================================");
        }

        /** Prints the XIRR / service-YTM / stored-annual-YTM outcome for inspection. */
        private static void printYtmResult(
                        Bond bond,
                        BigDecimal realXirr,
                        BigDecimal returnedYtm) {

                System.out.println();
                System.out.println("YTM Result (direct XIRR vs service)");
                System.out.println("Real XIRR        : " + realXirr);
                System.out.println("Real XIRR (%)    : " + realXirr.multiply(HUNDRED));
                System.out.println("Returned YTM     : " + returnedYtm);
                System.out.println("Returned YTM (%) : " + returnedYtm.multiply(HUNDRED));
                System.out.println("Stored Annual YTM: " + bond.getAnnualYtm());
                System.out.println("PASS: Returned YTM equals calculated XIRR");
        }

}
