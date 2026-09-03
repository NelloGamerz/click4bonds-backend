package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class XirrCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private final XirrCalculator calculator = new XirrCalculator();

    @Test
    void rejectsMalformedCashFlowsBeforeCalculation() {
        System.out.println(
                "\n========== TEST: rejectsMalformedCashFlowsBeforeCalculation ==========");

        IllegalArgumentException exception;

        System.out.println("Case 1: Null cash flows");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(null));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 2: Empty cash flows");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(List.of()));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 3: Only negative cash flow");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(flow(START, -100))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 4: Null cash flow entry");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        Arrays.asList(
                                null,
                                flow(START.plusYears(1), 100))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 5: Cash flow with null date");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(
                                new XirrCalculator.CashFlow(
                                        null,
                                        BigDecimal.TEN),
                                flow(START, -100))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 6: Cash flow with null amount");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(
                                new XirrCalculator.CashFlow(
                                        START,
                                        null),
                                flow(
                                        START.plusYears(1),
                                        100))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 7: Only positive cash flows");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(
                                flow(START, 100),
                                flow(START.plusYears(1), 10))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 8: Only negative cash flows");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(
                                flow(START, -100),
                                flow(START.plusYears(1), -10))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 9: All zero cash flows");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(
                                flow(START, 0),
                                flow(START.plusYears(1), 0))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println("Case 10: Zero in addition to negative and positive cash flows");
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(
                                flow(START, -100),
                                flow(START.plusYears(1), 0),
                                flow(START.plusYears(2), 100))));
        System.out.println("Exception: " + exception.getMessage());

        System.out.println(
                "PASS: All malformed cash flow scenarios were rejected.");
    }

    @Test
    void sortsWithoutMutatingAndUsesEarliestDateAsBase() {
        System.out.println(
                "\n========== TEST: sortsWithoutMutatingAndUsesEarliestDateAsBase ==========");

        List<XirrCalculator.CashFlow> flows = new ArrayList<>(List.of(
                flow(START.plusYears(1), 110),
                flow(START, -100)));

        System.out.println("Input cash flows before calculation: " + flows);

        BigDecimal result = calculator.calculate(flows);

        System.out.println("Calculated XIRR: " + result);
        System.out.println("Original first cash flow date: " + flows.get(0).date());

        assertEquals(
                new BigDecimal("0.100000"),
                result);

        assertEquals(
                START.plusYears(1),
                flows.get(0).date());

        System.out.println(
                "PASS: Cash flows were sorted internally without mutating the input.");
    }

    @Test
    void supportsDuplicateDatesAndSameDayOpposingCashFlows() {
        System.out.println(
                "\n========== TEST: supportsDuplicateDatesAndSameDayOpposingCashFlows ==========");

        List<XirrCalculator.CashFlow> flows = List.of(
                flow(START.plusYears(1), 100),
                flow(START, 10),
                flow(START, -110));

        System.out.println("Input cash flows: " + flows);

        BigDecimal result = calculator.calculate(flows);

        System.out.println("Calculated XIRR: " + result);

        assertEquals(
                new BigDecimal("0.000000"),
                result);

        System.out.println(
                "PASS: Duplicate dates and opposing same-day cash flows handled correctly.");
    }

    @Test
    void calculatesNormalNegativeAndZeroReturns() {
        System.out.println(
                "\n========== TEST: calculatesNormalNegativeAndZeroReturns ==========");

        List<XirrCalculator.CashFlow> negativeReturnFlows = List.of(
                flow(START, -100),
                flow(START.plusYears(1), 90));

        System.out.println(
                "Negative return cash flows: " + negativeReturnFlows);

        BigDecimal negativeReturn = calculator.calculate(negativeReturnFlows);

        System.out.println(
                "Calculated negative XIRR: " + negativeReturn);

        assertEquals(
                new BigDecimal("-0.100000"),
                negativeReturn);

        List<XirrCalculator.CashFlow> zeroReturnFlows = List.of(
                flow(START, -100),
                flow(START.plusYears(1), 100));

        System.out.println(
                "Zero return cash flows: " + zeroReturnFlows);

        BigDecimal zeroReturn = calculator.calculate(zeroReturnFlows);

        System.out.println(
                "Calculated zero XIRR: " + zeroReturn);

        assertEquals(
                new BigDecimal("0.000000"),
                zeroReturn);

        System.out.println(
                "PASS: Negative and zero XIRR returns calculated correctly.");
    }

    @Test
    void usesActualDaysAndHandlesLeapYearsAndLongDurations() {
        System.out.println(
                "\n========== TEST: usesActualDaysAndHandlesLeapYearsAndLongDurations ==========");

        BigDecimal fractionalAmount = BigDecimal.valueOf(
                100 * Math.pow(1.10, 182.0 / 365.0));

        List<XirrCalculator.CashFlow> fractionalFlows = List.of(
                flow(START, -100),
                new XirrCalculator.CashFlow(
                        START.plusDays(182),
                        fractionalAmount));

        System.out.println(
                "Fractional-period cash flows: " + fractionalFlows);
        System.out.println(
                "Expected annualized return: 10%");

        BigDecimal fractional = calculator.calculate(fractionalFlows);

        System.out.println(
                "Calculated fractional-period XIRR: " + fractional);

        assertEquals(
                0.10,
                fractional.doubleValue(),
                0.000001);

        List<XirrCalculator.CashFlow> longDurationFlows = List.of(
                flow(
                        LocalDate.of(2020, 2, 29),
                        -100),
                flow(
                        LocalDate.of(2050, 2, 28),
                        1_000));

        System.out.println(
                "Long-duration cash flows: " + longDurationFlows);

        BigDecimal longDuration = calculator.calculate(longDurationFlows);

        System.out.println(
                "Calculated long-duration XIRR: " + longDuration);
        System.out.println(
                "Long-duration XIRR as double: "
                        + longDuration.doubleValue());

        assertTrue(
                longDuration.doubleValue() > -1.0);

        assertFalse(
                Double.isNaN(longDuration.doubleValue()));

        assertFalse(
                Double.isInfinite(longDuration.doubleValue()));

        System.out.println(
                "PASS: Actual days, leap years, and long durations handled correctly.");
    }

    @Test
    void handlesHighReturnsAndReportsNoSolution() {
        System.out.println(
                "\n========== TEST: handlesHighReturnsAndReportsNoSolution ==========");

        List<XirrCalculator.CashFlow> highReturnFlows = List.of(
                flow(START, -1),
                flow(START.plusYears(1), 1_000_000));

        System.out.println(
                "High-return cash flows: " + highReturnFlows);

        BigDecimal highReturn = calculator.calculate(highReturnFlows);

        System.out.println(
                "Calculated high XIRR: " + highReturn);
        System.out.println(
                "Calculated high XIRR as double: "
                        + highReturn.doubleValue());
        System.out.println(
                "Calculated scale: " + highReturn.scale());

        assertTrue(
                highReturn.doubleValue() > -1.0);

        assertTrue(
                highReturn.scale() == 6);

        System.out.println(
                "Testing cash flows with no valid XIRR solution...");

        List<XirrCalculator.CashFlow> noSolutionFlows = List.of(
                flow(START, -100),
                flow(START, 110));

        System.out.println(
                "No-solution cash flows: " + noSolutionFlows);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> calculator.calculate(noSolutionFlows));

        System.out.println(
                "Exception message: " + exception.getMessage());

        assertTrue(
                exception.getMessage().contains("XIRR"));

        System.out.println(
                "PASS: High returns handled and no-solution case reported correctly.");
    }

    @Test
    void returnsDeterministicSixDecimalHalfUpResults() {
        System.out.println(
                "\n========== TEST: returnsDeterministicSixDecimalHalfUpResults ==========");

        List<XirrCalculator.CashFlow> flows = List.of(
                flow(START, -100),
                flow(
                        START.plusYears(1),
                        110.1234567));

        System.out.println("Input cash flows: " + flows);

        BigDecimal first = calculator.calculate(flows);
        BigDecimal second = calculator.calculate(flows);

        System.out.println(
                "First calculation result: " + first);
        System.out.println(
                "Second calculation result: " + second);
        System.out.println(
                "Result scale: " + first.scale());

        assertEquals(
                first,
                second);

        assertEquals(
                6,
                first.scale());

        assertEquals(
                new BigDecimal("0.101235"),
                first);

        System.out.println(
                "PASS: XIRR result is deterministic and rounded to six decimals.");
    }

    private XirrCalculator.CashFlow flow(
            LocalDate date,
            double amount) {
        return new XirrCalculator.CashFlow(
                date,
                BigDecimal.valueOf(amount));
    }
}
