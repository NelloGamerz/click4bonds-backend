// package com.click4bonds.app.Modules.Bond.Service;

// import static org.junit.jupiter.api.Assertions.*;

// import java.math.BigDecimal;
// import java.time.LocalDate;
// import java.util.List;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;

// import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;

// // import com.click4bonds.app.Modules.Bond.Model.AmortizationRule;
// // import com.click4bonds.app.Modules.Bond.Model.IpBasedAmortizationRule;
// // import com.click4bonds.app.Modules.Bond.Model.MaturitySchedule;

// class MaturityDescriptionParserTest {

//     private MaturityDescriptionParser parser;

//     @BeforeEach
//     void setUp() {

//         parser = new MaturityDescriptionParserImpl();
//     }

//     // ============================================================
//     // NORMAL MATURITY
//     // ============================================================

//     @Test
//     void shouldParseNormalMaturity() {

//         MaturitySchedule result = parser.parse(
//                 "26-09-2031",
//                 LocalDate.of(2031, 9, 26));

//         System.out.println(
//                 "\n===== NORMAL MATURITY =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Perpetual     : "
//                         + result.perpetual());

//         System.out.println(
//                 "Amortizing    : "
//                         + result.isAmortizing());

//         System.out.println(
//                 "Rules         : "
//                         + result.amortizationRules().size());

//         assertEquals(
//                 LocalDate.of(2031, 9, 26),
//                 result.maturityDate());

//         assertFalse(result.perpetual());

//         assertFalse(result.isAmortizing());

//         assertTrue(
//                 result.amortizationRules().isEmpty());
//     }

//     // ============================================================
//     // PERPETUAL
//     // ============================================================

//     @Test
//     void shouldParsePerpetual() {

//         MaturitySchedule result = parser.parse(
//                 "Perp",
//                 null);

//         System.out.println(
//                 "\n===== PERPETUAL BOND =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Perpetual     : "
//                         + result.perpetual());

//         System.out.println(
//                 "Rules         : "
//                         + result.amortizationRules().size());

//         assertTrue(result.perpetual());

//         assertNull(result.maturityDate());

//         assertTrue(
//                 result.amortizationRules().isEmpty());
//     }

//     @Test
//     void shouldParsePerpetualCaseInsensitive() {

//         MaturitySchedule result = parser.parse(
//                 "PERPETUAL",
//                 null);

//         System.out.println(
//                 "\n===== PERPETUAL CASE INSENSITIVE =====");

//         System.out.println(
//                 "Perpetual : "
//                         + result.perpetual());

//         assertTrue(result.perpetual());
//     }

//     // ============================================================
//     // ANNUAL AMORTIZATION
//     // ============================================================

//     @Test
//     void shouldParseAnnualAmortization() {

//         MaturitySchedule result = parser.parse(
//                 "9/11/2024 to 9/11/2033 (10% each year)",
//                 LocalDate.of(2033, 11, 9));

//         System.out.println(
//                 "\n===== ANNUAL AMORTIZATION =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Number Rules  : "
//                         + result.amortizationRules().size());

//         assertEquals(
//                 LocalDate.of(2033, 11, 9),
//                 result.maturityDate());

//         assertFalse(result.perpetual());

//         assertTrue(result.isAmortizing());

//         assertEquals(
//                 1,
//                 result.amortizationRules().size());

//         AmortizationRule rule = result.amortizationRules().get(0);

//         System.out.println(
//                 "Rule Type     : "
//                         + rule.getClass().getSimpleName());

//         System.out.println(
//                 "Rule          : "
//                         + rule);

//         assertEquals(
//                 new BigDecimal("10"),
//                 rule.percentage());
//     }

//     // ============================================================
//     // QUARTERLY AMORTIZATION
//     // ============================================================

//     @Test
//     void shouldParseQuarterlyAmortization() {

//         MaturitySchedule result = parser.parse(
//                 "01/10/2026 to 01/10/2027 (20% Quarterly)",
//                 LocalDate.of(2027, 10, 1));

//         System.out.println(
//                 "\n===== QUARTERLY AMORTIZATION =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Number Rules  : "
//                         + result.amortizationRules().size());

//         assertEquals(
//                 LocalDate.of(2027, 10, 1),
//                 result.maturityDate());

//         assertEquals(
//                 1,
//                 result.amortizationRules().size());

//         AmortizationRule rule = result.amortizationRules().get(0);

//         System.out.println(
//                 "Rule Type : "
//                         + rule.getClass().getSimpleName());

//         System.out.println(
//                 "Rule      : "
//                         + rule);

//         assertEquals(
//                 new BigDecimal("20"),
//                 rule.percentage());
//     }

//     // ============================================================
//     // QUARTELY MISSPELLING
//     // ============================================================

//     @Test
//     void shouldParseQuartelyMisspelling() {

//         MaturitySchedule result = parser.parse(
//                 "01/10/2026 to 01/10/2027 (20% Quartely)",
//                 LocalDate.of(2027, 10, 1));

//         System.out.println(
//                 "\n===== QUARTELY MISSPELLING =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Rules         : "
//                         + result.amortizationRules());

//         assertEquals(
//                 1,
//                 result.amortizationRules().size());
//     }

//     // ============================================================
//     // IP BASED AMORTIZATION
//     // ============================================================

//     @Test
//     void shouldParseIpBasedAmortization() {

//         MaturitySchedule result = parser.parse(
//                 "26-09-2031 (2.5% on Each IP till 2027)",
//                 LocalDate.of(2031, 9, 26));

//         System.out.println(
//                 "\n===== IP BASED AMORTIZATION =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Rules         : "
//                         + result.amortizationRules().size());

//         for (AmortizationRule rule : result.amortizationRules()) {

//             System.out.println(
//                     "Rule Type : "
//                             + rule.getClass().getSimpleName());

//             System.out.println(
//                     "Rule      : "
//                             + rule);
//         }

//         assertEquals(
//                 LocalDate.of(2031, 9, 26),
//                 result.maturityDate());

//         assertEquals(
//                 1,
//                 result.amortizationRules().size());

//         AmortizationRule rule = result.amortizationRules().get(0);

//         assertInstanceOf(
//                 IpBasedAmortizationRule.class,
//                 rule);

//         assertEquals(
//                 new BigDecimal("2.5"),
//                 rule.percentage());
//     }

//     // ============================================================
//     // MULTI-STAGE IP AMORTIZATION
//     // ============================================================

//     @Test
//     void shouldParseMultipleIpBasedAmortizationRules() {

//         String description = "26-09-2031 "
//                 + "(2.5% on Each IP till 2027 "
//                 + "and 10% on Each IP from 2028 to 2031)";

//         MaturitySchedule result = parser.parse(
//                 description,
//                 LocalDate.of(2031, 9, 26));

//         System.out.println(
//                 "\n===== MULTI-STAGE IP AMORTIZATION =====");

//         System.out.println(
//                 "Maturity Date : "
//                         + result.maturityDate());

//         System.out.println(
//                 "Number Rules  : "
//                         + result.amortizationRules().size());

//         int index = 1;

//         for (AmortizationRule rule : result.amortizationRules()) {

//             System.out.println(
//                     "\nRule #" + index);

//             System.out.println(
//                     "Type       : "
//                             + rule.getClass().getSimpleName());

//             System.out.println(
//                     "Percentage : "
//                             + rule.percentage());

//             System.out.println(
//                     "Start Year : "
//                             + rule.startDate());

//             System.out.println(
//                     "End Year   : "
//                             + rule.endDate());

//             index++;
//         }

//         assertEquals(
//                 2,
//                 result.amortizationRules().size());

//         AmortizationRule firstRule = result.amortizationRules().get(0);

//         AmortizationRule secondRule = result.amortizationRules().get(1);

//         assertEquals(
//                 new BigDecimal("2.5"),
//                 firstRule.percentage());

//         assertEquals(
//                 2027,
//                 firstRule.endDate());

//         assertEquals(
//                 new BigDecimal("10"),
//                 secondRule.percentage());

//         assertEquals(
//                 2028,
//                 secondRule.startDate());

//         assertEquals(
//                 2031,
//                 secondRule.endDate());
//     }

//     // ============================================================
//     // INVALID DESCRIPTION
//     // ============================================================

//     @Test
//     void shouldRejectUnsupportedDescription() {

//         String description =
//                 "Some random maturity description";

//         IllegalArgumentException exception =
//                 assertThrows(
//                         IllegalArgumentException.class,
//                         () -> parser.parse(
//                                 description,
//                                 LocalDate.of(2031, 9, 26)
//                         )
//                 );

//         System.out.println(
//                 "\n===== INVALID DESCRIPTION ====="
//         );

//         System.out.println(
//                 "Input     : "
//                         + description
//         );

//         System.out.println(
//                 "Exception : "
//                         + exception.getMessage()
//         );

//         assertTrue(
//                 exception.getMessage()
//                         .contains(
//                                 "Unsupported maturity description"
//                         )
//         );
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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

        System.out.println(
                "\n===== NORMAL MATURITY =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Perpetual     : "
                        + result.perpetual());

        System.out.println(
                "Amortizing    : "
                        + result.isAmortizing());

        System.out.println(
                "Rules         : "
                        + result.amortizationRules().size());

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

        System.out.println(
                "\n===== PERPETUAL BOND =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Perpetual     : "
                        + result.perpetual());

        System.out.println(
                "Rules         : "
                        + result.amortizationRules().size());

        assertTrue(result.perpetual());

        assertNull(result.maturityDate());

        assertTrue(
                result.amortizationRules().isEmpty());
    }

    @Test
    void shouldParsePerpetualCaseInsensitive() {

        MaturitySchedule result = parser.parse(
                "PERPETUAL",
                null);

        System.out.println(
                "\n===== PERPETUAL CASE INSENSITIVE =====");

        System.out.println(
                "Perpetual : "
                        + result.perpetual());

        assertTrue(result.perpetual());
    }

    // ============================================================
    // ANNUAL AMORTIZATION
    // ============================================================

    @Test
    void shouldParseAnnualAmortization() {

        MaturitySchedule result = parser.parse(
                "9/11/2024 to 9/11/2033 (10% each year)",
                LocalDate.of(2033, 11, 9));

        System.out.println(
                "\n===== ANNUAL AMORTIZATION =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Number Rules  : "
                        + result.amortizationRules().size());

        assertEquals(
                LocalDate.of(2033, 11, 9),
                result.maturityDate());

        assertFalse(result.perpetual());

        assertTrue(result.isAmortizing());

        assertEquals(
                1,
                result.amortizationRules().size());

        AmortizationRule rule = result.amortizationRules().get(0);

        System.out.println(
                "Rule Type     : "
                        + rule.getClass().getSimpleName());

        System.out.println(
                "Rule          : "
                        + rule);

        assertEquals(
                new BigDecimal("10"),
                rule.percentage());
    }

    // ============================================================
    // QUARTERLY AMORTIZATION
    // ============================================================

    @Test
    void shouldParseQuarterlyAmortization() {

        MaturitySchedule result = parser.parse(
                "01/10/2026 to 01/10/2027 (20% Quarterly)",
                LocalDate.of(2027, 10, 1));

        System.out.println(
                "\n===== QUARTERLY AMORTIZATION =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Number Rules  : "
                        + result.amortizationRules().size());

        assertEquals(
                LocalDate.of(2027, 10, 1),
                result.maturityDate());

        assertEquals(
                1,
                result.amortizationRules().size());

        AmortizationRule rule = result.amortizationRules().get(0);

        System.out.println(
                "Rule Type : "
                        + rule.getClass().getSimpleName());

        System.out.println(
                "Rule      : "
                        + rule);

        assertEquals(
                new BigDecimal("20"),
                rule.percentage());
    }

    // ============================================================
    // QUARTELY MISSPELLING
    // ============================================================

    @Test
    void shouldParseQuartelyMisspelling() {

        MaturitySchedule result = parser.parse(
                "01/10/2026 to 01/10/2027 (20% Quartely)",
                LocalDate.of(2027, 10, 1));

        System.out.println(
                "\n===== QUARTELY MISSPELLING =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Rules         : "
                        + result.amortizationRules());

        assertEquals(
                1,
                result.amortizationRules().size());
    }

    // ============================================================
    // IP BASED AMORTIZATION
    // ============================================================

    @Test
    void shouldParseIpBasedAmortization() {

        MaturitySchedule result = parser.parse(
                "26-09-2031 (2.5% on Each IP till 2027)",
                LocalDate.of(2031, 9, 26));

        System.out.println(
                "\n===== IP BASED AMORTIZATION =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Rules         : "
                        + result.amortizationRules().size());

        for (AmortizationRule rule : result.amortizationRules()) {

            System.out.println(
                    "Rule Type : "
                            + rule.getClass().getSimpleName());

            System.out.println(
                    "Rule      : "
                            + rule);
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

        System.out.println(
                "\n===== MULTI-STAGE IP AMORTIZATION =====");

        System.out.println(
                "Maturity Date : "
                        + result.maturityDate());

        System.out.println(
                "Number Rules  : "
                        + result.amortizationRules().size());

        int index = 1;

        for (AmortizationRule rule : result.amortizationRules()) {

            System.out.println(
                    "\nRule #" + index);

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

            index++;
        }

        assertEquals(
                2,
                result.amortizationRules().size());

        AmortizationRule firstRule = result.amortizationRules().get(0);

        AmortizationRule secondRule = result.amortizationRules().get(1);

        // First rule:
        // 2.5% on each IP till 2027
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

        // Second rule:
        // 10% on each IP from 2028 to 2031
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

        System.out.println(
                "\n===== INVALID DESCRIPTION =====");

        System.out.println(
                "Input     : "
                        + description);

        System.out.println(
                "Exception : "
                        + exception.getMessage());

        assertTrue(
                exception.getMessage()
                        .contains(
                                "Unsupported maturity description"));
    }
}
