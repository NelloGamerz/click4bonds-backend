package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;

class MaturityDescriptionParserTest {

        private MaturityDescriptionParser parser;

        @BeforeEach
        void setUp() {
                parser = new MaturityDescriptionParserImpl();
        }

        // ============================================================
        // NORMAL MATURITY
        // ============================================================

        @Test
        void shouldParseNormalMaturity() {

                MaturitySchedule result = parser.parse(
                                "26-09-2031",
                                LocalDate.of(2031, 9, 26));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("NORMAL MATURITY");
                System.out.println("============================================================");

                System.out.println("Input Description : 26-09-2031");
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());
                System.out.println("Rules             : " + result.amortizationRules());

                assertEquals(
                                LocalDate.of(2031, 9, 26),
                                result.maturityDate());

                assertFalse(result.perpetual());

                assertFalse(result.isAmortizing());

                assertTrue(
                                result.amortizationRules().isEmpty());
        }

        // ============================================================
        // PERPETUAL
        // ============================================================

        @Test
        void shouldParsePerpetual() {

                MaturitySchedule result = parser.parse(
                                "Perp",
                                null);

                System.out.println();
                System.out.println("============================================================");
                System.out.println("PERPETUAL BOND");
                System.out.println("============================================================");

                System.out.println("Input Description : Perp");
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());
                System.out.println("Rules             : " + result.amortizationRules());

                assertTrue(result.perpetual());

                assertNull(result.maturityDate());

                assertTrue(
                                result.amortizationRules().isEmpty());
        }

        // ============================================================
        // PERPETUAL CASE INSENSITIVE
        // ============================================================

        @Test
        void shouldParsePerpetualCaseInsensitive() {

                MaturitySchedule result = parser.parse(
                                "PERPETUAL",
                                null);

                System.out.println();
                System.out.println("============================================================");
                System.out.println("PERPETUAL CASE INSENSITIVE");
                System.out.println("============================================================");

                System.out.println("Input Description : PERPETUAL");
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());

                assertTrue(result.perpetual());
        }

        // ============================================================
        // ANNUAL AMORTIZATION
        // ============================================================

        @Test
        void shouldParseAnnualAmortization() {

                String description = "9/11/2024 to 9/11/2033 (10% each year)";

                MaturitySchedule result = parser.parse(
                                description,
                                LocalDate.of(2033, 11, 9));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("ANNUAL AMORTIZATION");
                System.out.println("============================================================");

                System.out.println("Input Description : " + description);
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());

                int index = 1;

                for (AmortizationRule rule : result.amortizationRules()) {

                        System.out.println();
                        System.out.println("Rule #" + index);
                        System.out.println("------------------------------------------------------------");
                        System.out.println("Rule Type         : " + rule.getClass().getSimpleName());
                        System.out.println("Percentage        : " + rule.percentage());
                        System.out.println("Start Date        : " + rule.startDate());
                        System.out.println("End Date          : " + rule.endDate());
                        System.out.println("Frequency         : "
                                        + (rule instanceof FixedFrequencyAmortizationRule fixedRule
                                                        ? fixedRule.frequency()
                                                        : "N/A"));
                        System.out.println("Rule Object       : " + rule);

                        index++;
                }

                assertEquals(
                                LocalDate.of(2033, 11, 9),
                                result.maturityDate());

                assertFalse(result.perpetual());

                assertTrue(result.isAmortizing());

                assertEquals(
                                1,
                                result.amortizationRules().size());

                AmortizationRule rule = result.amortizationRules().get(0);

                assertEquals(
                                new BigDecimal("10"),
                                rule.percentage());
        }

        // ============================================================
        // QUARTERLY AMORTIZATION
        // ============================================================

        @Test
        void shouldParseQuarterlyAmortization() {

                String description = "01/10/2026 to 01/10/2027 (20% Quarterly)";

                MaturitySchedule result = parser.parse(
                                description,
                                LocalDate.of(2027, 10, 1));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("QUARTERLY AMORTIZATION");
                System.out.println("============================================================");

                System.out.println("Input Description : " + description);
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());

                int index = 1;

                for (AmortizationRule rule : result.amortizationRules()) {

                        System.out.println();
                        System.out.println("Rule #" + index);
                        System.out.println("------------------------------------------------------------");
                        System.out.println("Rule Type         : " + rule.getClass().getSimpleName());
                        System.out.println("Percentage        : " + rule.percentage());
                        System.out.println("Start Date        : " + rule.startDate());
                        System.out.println("End Date          : " + rule.endDate());
                        System.out.println("Frequency         : "
                                        + (rule instanceof FixedFrequencyAmortizationRule fixedRule
                                                        ? fixedRule.frequency()
                                                        : "N/A"));
                        System.out.println("Rule Object       : " + rule);

                        index++;
                }

                assertEquals(
                                LocalDate.of(2027, 10, 1),
                                result.maturityDate());

                assertEquals(
                                1,
                                result.amortizationRules().size());

                AmortizationRule rule = result.amortizationRules().get(0);

                assertEquals(
                                new BigDecimal("20"),
                                rule.percentage());
        }

        // ============================================================
        // QUARTELY MISSPELLING
        // ============================================================

        @Test
        void shouldParseQuartelyMisspelling() {

                String description = "01/10/2026 to 01/10/2027 (20% Quartely)";

                MaturitySchedule result = parser.parse(
                                description,
                                LocalDate.of(2027, 10, 1));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("QUARTELY MISSPELLING");
                System.out.println("============================================================");

                System.out.println("Input Description : " + description);
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());

                int index = 1;

                for (AmortizationRule rule : result.amortizationRules()) {

                        System.out.println();
                        System.out.println("Rule #" + index);
                        System.out.println("------------------------------------------------------------");
                        System.out.println("Rule Type         : " + rule.getClass().getSimpleName());
                        System.out.println("Percentage        : " + rule.percentage());
                        System.out.println("Start Date        : " + rule.startDate());
                        System.out.println("End Date          : " + rule.endDate());
                        System.out.println("Frequency         : "
                                        + (rule instanceof FixedFrequencyAmortizationRule fixedRule
                                                        ? fixedRule.frequency()
                                                        : "N/A"));

                        index++;
                }

                assertEquals(
                                1,
                                result.amortizationRules().size());
        }

        // ============================================================
        // IP BASED AMORTIZATION
        // ============================================================

        @Test
        void shouldParseIpBasedAmortization() {

                String description = "26-09-2031 (2.5% on Each IP till 2027)";

                MaturitySchedule result = parser.parse(
                                description,
                                LocalDate.of(2031, 9, 26));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("IP BASED AMORTIZATION");
                System.out.println("============================================================");

                System.out.println("Input Description : " + description);
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());

                int index = 1;

                for (AmortizationRule rule : result.amortizationRules()) {

                        System.out.println();
                        System.out.println("Rule #" + index);
                        System.out.println("------------------------------------------------------------");
                        System.out.println("Rule Type         : " + rule.getClass().getSimpleName());
                        System.out.println("Percentage        : " + rule.percentage());
                        System.out.println("Start Date        : " + rule.startDate());
                        System.out.println("End Date          : " + rule.endDate());
                        System.out.println("Rule Object       : " + rule);

                        index++;
                }

                assertEquals(
                                LocalDate.of(2031, 9, 26),
                                result.maturityDate());

                assertEquals(
                                1,
                                result.amortizationRules().size());

                AmortizationRule rule = result.amortizationRules().get(0);

                assertInstanceOf(
                                IpBasedAmortizationRule.class,
                                rule);

                assertEquals(
                                new BigDecimal("2.5"),
                                rule.percentage());
        }

        // ============================================================
        // MULTI-STAGE IP AMORTIZATION
        // ============================================================

        @Test
        void shouldParseMultipleIpBasedAmortizationRules() {

                String description = "26-09-2031 "
                                + "(2.5% on Each IP till 2027 "
                                + "and 10% on Each IP from 2028 to 2031)";

                MaturitySchedule result = parser.parse(
                                description,
                                LocalDate.of(2031, 9, 26));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("MULTI-STAGE IP AMORTIZATION");
                System.out.println("============================================================");

                System.out.println("Input Description : " + description);
                System.out.println("Maturity Date     : " + result.maturityDate());
                System.out.println("Perpetual         : " + result.perpetual());
                System.out.println("Amortizing        : " + result.isAmortizing());
                System.out.println("Rules Count       : " + result.amortizationRules().size());

                int index = 1;

                for (AmortizationRule rule : result.amortizationRules()) {

                        System.out.println();
                        System.out.println("Rule #" + index);
                        System.out.println("------------------------------------------------------------");

                        System.out.println(
                                        "Type       : "
                                                        + rule.getClass().getSimpleName());

                        System.out.println(
                                        "Percentage : "
                                                        + rule.percentage());

                        System.out.println(
                                        "Start Date : "
                                                        + rule.startDate());

                        System.out.println(
                                        "End Date   : "
                                                        + rule.endDate());

                        System.out.println(
                                        "Rule Object: "
                                                        + rule);

                        index++;
                }

                System.out.println();
                System.out.println("Expected First Rule:");
                System.out.println("  Percentage : 2.5");
                System.out.println("  Start Date : 2024-01-01");
                System.out.println("  End Date   : 2027-12-31");

                System.out.println();
                System.out.println("Expected Second Rule:");
                System.out.println("  Percentage : 10");
                System.out.println("  Start Date : 2028-01-01");
                System.out.println("  End Date   : 2031-12-31");

                assertEquals(
                                2,
                                result.amortizationRules().size());

                AmortizationRule firstRule = result.amortizationRules().get(0);

                AmortizationRule secondRule = result.amortizationRules().get(1);

                // --------------------------------------------------------
                // FIRST RULE
                // 2.5% on each IP till 2027
                // --------------------------------------------------------

                assertInstanceOf(
                                IpBasedAmortizationRule.class,
                                firstRule);

                assertEquals(
                                new BigDecimal("2.5"),
                                firstRule.percentage());

                assertEquals(
                                LocalDate.of(2024, 1, 1),
                                firstRule.startDate());

                assertEquals(
                                LocalDate.of(2027, 12, 31),
                                firstRule.endDate());

                // --------------------------------------------------------
                // SECOND RULE
                // 10% on each IP from 2028 to 2031
                // --------------------------------------------------------

                assertInstanceOf(
                                IpBasedAmortizationRule.class,
                                secondRule);

                assertEquals(
                                new BigDecimal("10"),
                                secondRule.percentage());

                assertEquals(
                                LocalDate.of(2028, 1, 1),
                                secondRule.startDate());

                assertEquals(
                                LocalDate.of(2031, 12, 31),
                                secondRule.endDate());
        }

        // ============================================================
        // INVALID DESCRIPTION
        // ============================================================

        @Test
        void shouldRejectUnsupportedDescription() {

                String description = "Some random maturity description";

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> parser.parse(
                                                description,
                                                LocalDate.of(2031, 9, 26)));

                System.out.println();
                System.out.println("============================================================");
                System.out.println("INVALID DESCRIPTION");
                System.out.println("============================================================");

                System.out.println("Input Description : " + description);
                System.out.println("Exception Type    : "
                                + exception.getClass().getSimpleName());
                System.out.println("Exception Message : "
                                + exception.getMessage());

                assertTrue(
                                exception.getMessage()
                                                .contains(
                                                                "Unsupported maturity description"));
        }
}
