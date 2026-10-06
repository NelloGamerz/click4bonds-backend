package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Exception.UnsupportedRecordDateDescriptionException;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Verifies that record dates are derived from the description text alone.
 *
 * <p>
 * The concrete dates asserted here are the expected values of generic date
 * arithmetic on a known description and a known payment date. Nothing in the
 * production code contains a concrete record date or an ISIN-specific rule.
 */
class RecordDateParserTest {

        private static final LocalDate OCTOBER_PAYMENT = LocalDate.of(2026, 10, 23);

        private RecordDateParser parser;

        @BeforeEach
        void setUp() {
                parser = new RecordDateParserImpl();
        }

        // ============================================================
        // THE PRIMARY FORM
        // ============================================================

        @Test
        void parsesDaysPriorToInterestPaymentDate() {

                Optional<LocalDate> recordDate = parser.parse(
                                "15 days prior to interest payment date",
                                OCTOBER_PAYMENT);

                System.out.println();
                System.out.println("============================================================");
                System.out.println("15 DAYS PRIOR TO INTEREST PAYMENT DATE");
                System.out.println("============================================================");
                System.out.println("Description : 15 days prior to interest payment date");
                System.out.println("Payment Date: " + OCTOBER_PAYMENT);
                System.out.println("Record Date : " + recordDate.orElse(null));
                System.out.println("Offset Days : " + parser.parseOffsetDays(
                                "15 days prior to interest payment date").orElse(null));

                assertEquals(LocalDate.of(2026, 10, 8), recordDate.orElseThrow());
        }

        // ============================================================
        // WORDING / FORMATTING TOLERANCE
        // ============================================================

        @Test
        void ignoresCaseAndSurroundingPunctuation() {

                /*
                 * The same rule written three ways must produce one record date.
                 */
                assertEquals(
                                LocalDate.of(2026, 10, 8),
                                parser.parse(
                                                "15 DAYS PRIOR TO INTEREST PAYMENT DATE",
                                                OCTOBER_PAYMENT).orElseThrow());

                assertEquals(
                                LocalDate.of(2026, 10, 8),
                                parser.parse(
                                                "15 days prior to interest payment date.",
                                                OCTOBER_PAYMENT).orElseThrow());

                assertEquals(
                                LocalDate.of(2026, 10, 8),
                                parser.parse(
                                                "15-Days prior to interest payment date",
                                                OCTOBER_PAYMENT).orElseThrow());

                System.out.println();
                System.out.println("Case, trailing period and hyphen variants all resolve to "
                                + LocalDate.of(2026, 10, 8));
        }

        @Test
        void collapsesExtraWhitespace() {

                assertEquals(
                                LocalDate.of(2026, 10, 8),
                                parser.parse(
                                                "  15   days    prior   to   interest    payment    date  ",
                                                OCTOBER_PAYMENT).orElseThrow());
        }

        @Test
        void supportsEquivalentWording() {

                /*
                 * All of these say the same thing as the primary form.
                 */
                List<String> descriptions = List.of(
                                "15 days before interest payment date",
                                "15 days prior to payment date",
                                "15 days before payment date",
                                "15 days prior to interest payment",
                                "Record date shall be 15 days prior to interest payment date",
                                "15 days prior to each interest payment date",
                                "15 days prior",
                                "The record date is 15 days prior to the interest payment date");

                for (String description : descriptions) {

                        assertEquals(
                                        LocalDate.of(2026, 10, 8),
                                        parser.parse(description, OCTOBER_PAYMENT).orElseThrow(),
                                        "Failed for description: " + description);
                }

                System.out.println();
                System.out.println("All " + descriptions.size()
                                + " equivalent wordings resolved to " + LocalDate.of(2026, 10, 8));
        }

        // ============================================================
        // THE NUMBER COMES FROM THE DESCRIPTION
        // ============================================================

        @Test
        void readsTheOffsetFromTheDescriptionRatherThanAssumingFifteen() {

                assertEquals(
                                OCTOBER_PAYMENT.minusDays(7),
                                parser.parse(
                                                "7 days prior to interest payment date",
                                                OCTOBER_PAYMENT).orElseThrow());

                assertEquals(
                                OCTOBER_PAYMENT.minusDays(10),
                                parser.parse(
                                                "10 days before coupon date",
                                                OCTOBER_PAYMENT).orElseThrow());

                assertEquals(
                                OCTOBER_PAYMENT.minusDays(1),
                                parser.parse(
                                                "1 day prior to interest payment date",
                                                OCTOBER_PAYMENT).orElseThrow());

                assertEquals(
                                OCTOBER_PAYMENT.minusDays(2),
                                parser.parse(
                                                "2 days before coupon",
                                                OCTOBER_PAYMENT).orElseThrow());

                assertEquals(
                                7,
                                parser.parseOffsetDays(
                                                "7 days prior to interest payment date").orElseThrow());

                System.out.println();
                System.out.println("Offsets are read from the text, never defaulted");
        }

        @Test
        void supportsAnOffsetAfterThePaymentDate() {

                /*
                 * The direction is part of the rule, so "after" must shift
                 * forward instead of backward.
                 */
                assertEquals(
                                OCTOBER_PAYMENT.plusDays(5),
                                parser.parse(
                                                "5 days after payment date",
                                                OCTOBER_PAYMENT).orElseThrow());
        }

        // ============================================================
        // NO RECORD-DATE RULE
        // ============================================================

        @Test
        void returnsNoRecordDateForNullOrBlankDescription() {

                assertTrue(parser.parse(null, OCTOBER_PAYMENT).isEmpty());
                assertTrue(parser.parse("", OCTOBER_PAYMENT).isEmpty());
                assertTrue(parser.parse("   ", OCTOBER_PAYMENT).isEmpty());

                System.out.println();
                System.out.println("Null / blank descriptions yield no record date");
        }

        @Test
        void returnsNoRecordDateForNotApplicableMarkers() {

                List<String> markers = List.of(
                                "NA",
                                "na",
                                "N/A",
                                "N.A.",
                                "N / A",
                                "Not Applicable",
                                "NOT APPLICABLE",
                                "Nil",
                                "None",
                                "-",
                                "--");

                for (String marker : markers) {

                        assertTrue(
                                        parser.parse(marker, OCTOBER_PAYMENT).isEmpty(),
                                        "Expected no record date for marker: " + marker);

                        assertFalse(
                                        parser.isApplicable(marker),
                                        "Expected not applicable for marker: " + marker);
                }

                System.out.println();
                System.out.println("All " + markers.size()
                                + " not-applicable markers yield no record date");
        }

        // ============================================================
        // UNSUPPORTED WORDING MUST NOT BE GUESSED
        // ============================================================

        @Test
        void throwsForUnsupportedDescription() {

                /*
                 * "21st of every month" is a real source format but it is not a
                 * day offset. It must be rejected rather than silently read as
                 * something else.
                 */
                List<String> unsupported = List.of(
                                "21st of every month",
                                "20/09/2026",
                                "on the record date",
                                "3 days prior to the last working day");

                for (String description : unsupported) {

                        UnsupportedRecordDateDescriptionException exception = assertThrows(
                                        UnsupportedRecordDateDescriptionException.class,
                                        () -> parser.parse(description, OCTOBER_PAYMENT),
                                        "Expected rejection for: " + description);

                        assertEquals(
                                        "Unsupported record date description: " + description,
                                        exception.getMessage(),
                                        "The error must quote the original description");
                }

                System.out.println();
                System.out.println("Unsupported descriptions fail loudly and quote the source text");
        }

        @Test
        void throwsForAnAmbiguousOffsetRange() {

                /*
                 * "15-20 days prior" has no single answer. Picking either bound
                 * would be a guess, so the description is rejected.
                 */
                assertThrows(
                                UnsupportedRecordDateDescriptionException.class,
                                () -> parser.parse(
                                                "15-20 days prior to interest payment date",
                                                OCTOBER_PAYMENT));
        }

        @Test
        void throwsForConflictingOffsetsInOneDescription() {

                assertThrows(
                                UnsupportedRecordDateDescriptionException.class,
                                () -> parser.parse(
                                                "15 days prior to interest payment date and 7 days prior to maturity",
                                                OCTOBER_PAYMENT));
        }

        // ============================================================
        // ARGUMENT VALIDATION
        // ============================================================

        @Test
        void rejectsNullPaymentDate() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> parser.parse(
                                                "15 days prior to interest payment date",
                                                null));
        }

        @Test
        void rejectsNullBond() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> parser.calculateRecordDate(null, OCTOBER_PAYMENT));
        }

        // ============================================================
        // EVERY PAYMENT DATE GETS ITS OWN RECORD DATE
        // ============================================================

        @Test
        void derivesARecordDatePerMonthlyPayment() {

                Bond bond = monthlyBond("23rd of every month");
                bond.setRecordDateDescription("15 days prior to interest payment date");

                List<LocalDate> paymentDates = List.of(
                                LocalDate.of(2026, 10, 23),
                                LocalDate.of(2026, 11, 23),
                                LocalDate.of(2026, 12, 23));

                Map<LocalDate, LocalDate> recordDates = parser.calculateRecordDates(
                                bond,
                                paymentDates);

                System.out.println();
                System.out.println("============================================================");
                System.out.println("RECORD DATE PER MONTHLY PAYMENT");
                System.out.println("============================================================");
                recordDates.forEach((paymentDate, recordDate) -> System.out.println(
                                "paymentDate=" + paymentDate + " recordDate=" + recordDate));

                /*
                 * One record date per payment date - not one record date reused
                 * for the whole schedule.
                 */
                assertEquals(3, recordDates.size());
                assertEquals(LocalDate.of(2026, 10, 8), recordDates.get(LocalDate.of(2026, 10, 23)));
                assertEquals(LocalDate.of(2026, 11, 8), recordDates.get(LocalDate.of(2026, 11, 23)));
                assertEquals(LocalDate.of(2026, 12, 8), recordDates.get(LocalDate.of(2026, 12, 23)));
        }

        @Test
        void calculateRecordDatesHonoursTheBondDescription() {

                Bond bond = monthlyBond("23rd of every month");
                bond.setRecordDateDescription("2 days before coupon");

                assertEquals(
                                LocalDate.of(2026, 10, 21),
                                parser.calculateRecordDate(bond, LocalDate.of(2026, 10, 23))
                                                .orElseThrow());

                assertTrue(
                                parser.calculateRecordDates(
                                                bond,
                                                List.of(LocalDate.of(2026, 10, 23)))
                                                .containsKey(LocalDate.of(2026, 10, 23)));
        }

        @Test
        void bondWithNoDescriptionProducesNoRecordDates() {

                Bond bond = monthlyBond("23rd of every month");
                bond.setRecordDateDescription("NA");

                assertTrue(parser.calculateRecordDates(
                                bond,
                                List.of(LocalDate.of(2026, 10, 23))).isEmpty());

                assertTrue(parser.calculateRecordDate(
                                bond,
                                LocalDate.of(2026, 10, 23)).isEmpty());
        }

        // ============================================================
        // HELPERS
        // ============================================================

        private static Bond monthlyBond(String ipDateDescription) {

                Bond bond = new Bond();
                bond.setName("Record Date Parser Test Bond");
                bond.setIsin("INE000000000");
                bond.setPrice(new java.math.BigDecimal("100.00"));
                bond.setCouponRate(new java.math.BigDecimal("12.00"));
                bond.setCouponFrequency(CouponFrequency.MONTHLY);
                bond.setIpDateDescription(ipDateDescription);
                bond.setMaturityType(MaturityType.FIXED);
                bond.setMaturityDate(LocalDate.of(2031, 7, 23));
                return bond;
        }
}
