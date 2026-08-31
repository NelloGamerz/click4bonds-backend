// package com.click4bonds.app.Modules.Bond.Service;

// import static org.junit.jupiter.api.Assertions.*;
// import java.math.BigDecimal;
// import org.junit.jupiter.api.Test;

// class IpBasedAmortizationRuleTest {
//     @Test
//     void shouldCreateValidIpBasedAmortizationRule() {
//         IpBasedAmortizationRule rule = new IpBasedAmortizationRule(new BigDecimal("2.5"), 2024, 2027);
//         System.out.println("\n===== IP BASED AMORTIZATION RULE =====");
//         System.out.println("Percentage : " + rule.percentage() + "%");
//         System.out.println("Start Year : " + rule.startYear());
//         System.out.println("End Year : " + rule.endYear());
//         assertEquals(new BigDecimal("2.5"), rule.percentage());
//         assertEquals(2024, rule.startYear());
//         assertEquals(2027, rule.endYear());
//     }

//     @Test
//     void shouldCreateRuleForSingleYear() {
//         IpBasedAmortizationRule rule = new IpBasedAmortizationRule(new BigDecimal("10"), 2028, 2028);
//         System.out.println("\n===== SINGLE YEAR IP RULE =====");
//         System.out.println("Percentage : " + rule.percentage() + "%");
//         System.out.println("Start Year : " + rule.startYear());
//         System.out.println("End Year : " + rule.endYear());
//         assertEquals(new BigDecimal("10"), rule.percentage());
//         assertEquals(2028, rule.startYear());
//         assertEquals(2028, rule.endYear());
//     }

//     @Test
//     void shouldRejectNullPercentage() {
//         assertThrows(IllegalArgumentException.class, () -> new IpBasedAmortizationRule(null, 2024, 2027));
//         System.out.println("\nPASS: Null percentage rejected");
//     }

//     @Test
//     void shouldRejectZeroPercentage() {
//         assertThrows(IllegalArgumentException.class, () -> new IpBasedAmortizationRule(BigDecimal.ZERO, 2024, 2027));
//         System.out.println("\nPASS: Zero percentage rejected");
//     }

//     @Test
//     void shouldRejectPercentageGreaterThan100() {
//         assertThrows(IllegalArgumentException.class,
//                 () -> new IpBasedAmortizationRule(new BigDecimal("100.01"), 2024, 2027));
//         System.out.println("\nPASS: Percentage > 100 rejected");
//     }

//     @Test
//     void shouldRejectInvalidYearRange() {
//         assertThrows(IllegalArgumentException.class,
//                 () -> new IpBasedAmortizationRule(new BigDecimal("2.5"), 2028, 2027));
//         System.out.println("\nPASS: Invalid year range rejected");
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class IpBasedAmortizationRuleTest {

    @Test
    void shouldCreateValidIpBasedAmortizationRule() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2027, 12, 31);

        IpBasedAmortizationRule rule = new IpBasedAmortizationRule(
                new BigDecimal("2.5"),
                startDate,
                endDate);

        System.out.println("\n===== IP BASED AMORTIZATION RULE =====");
        System.out.println("Percentage : " + rule.percentage() + "%");
        System.out.println("Start Date : " + rule.startDate());
        System.out.println("End Date   : " + rule.endDate());

        assertEquals(new BigDecimal("2.5"), rule.percentage());
        assertEquals(startDate, rule.startDate());
        assertEquals(endDate, rule.endDate());
    }

    @Test
    void shouldCreateRuleForSingleDay() {
        LocalDate date = LocalDate.of(2028, 1, 1);

        IpBasedAmortizationRule rule = new IpBasedAmortizationRule(
                new BigDecimal("10"),
                date,
                date);

        System.out.println("\n===== SINGLE DAY IP RULE =====");
        System.out.println("Percentage : " + rule.percentage() + "%");
        System.out.println("Start Date : " + rule.startDate());
        System.out.println("End Date   : " + rule.endDate());

        assertEquals(new BigDecimal("10"), rule.percentage());
        assertEquals(date, rule.startDate());
        assertEquals(date, rule.endDate());
    }

    @Test
    void shouldRejectNullPercentage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpBasedAmortizationRule(
                        null,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2027, 12, 31)));

        System.out.println("\nPASS: Null percentage rejected");
    }

    @Test
    void shouldRejectZeroPercentage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpBasedAmortizationRule(
                        BigDecimal.ZERO,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2027, 12, 31)));

        System.out.println("\nPASS: Zero percentage rejected");
    }

    @Test
    void shouldRejectPercentageGreaterThan100() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpBasedAmortizationRule(
                        new BigDecimal("100.01"),
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2027, 12, 31)));

        System.out.println("\nPASS: Percentage > 100 rejected");
    }

    @Test
    void shouldRejectNullStartDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpBasedAmortizationRule(
                        new BigDecimal("2.5"),
                        null,
                        LocalDate.of(2027, 12, 31)));

        System.out.println("\nPASS: Null start date rejected");
    }

    @Test
    void shouldRejectNullEndDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpBasedAmortizationRule(
                        new BigDecimal("2.5"),
                        LocalDate.of(2024, 1, 1),
                        null));

        System.out.println("\nPASS: Null end date rejected");
    }

    @Test
    void shouldRejectInvalidDateRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpBasedAmortizationRule(
                        new BigDecimal("2.5"),
                        LocalDate.of(2028, 1, 1),
                        LocalDate.of(2027, 12, 31)));

        System.out.println("\nPASS: Invalid date range rejected");
    }
}
