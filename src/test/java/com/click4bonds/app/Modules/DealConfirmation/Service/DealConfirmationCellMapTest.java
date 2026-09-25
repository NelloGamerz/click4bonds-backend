package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

/**
 * Pins the letter's layout.
 *
 * <p>The addresses are the whole point of this class, so they are asserted one
 * by one. Without this, a template revision or a careless refactor could move a
 * value into the wrong cell and every other test would still pass — the figures
 * would be right, and printed against the wrong labels.</p>
 *
 * <p>The label positions these addresses correspond to are asserted separately,
 * against the real workbook, in {@code XlsxTemplateWriterTest}. Between them, a
 * mismatch cannot survive.</p>
 */
class DealConfirmationCellMapTest {

    private final DocumentProperties properties = new DocumentProperties();

    private final DealConfirmationCellMap cellMap = new DealConfirmationCellMap(properties);

    @Test
    void writesTheDealTermsIntoTheLabelValueRows() {

        Map<String, Object> cells = cellMap.toCells(values());

        /*
         * Rows 12 to 29 put the label in column A and the value in column C, so
         * every value address here is a C.
         */
        assertEquals("Our Sale", cells.get("C12"));
        assertEquals("ICCL", cells.get("C13"));
        assertEquals(LocalDate.of(2026, 9, 25), cells.get("C14"));
        assertEquals(LocalDate.of(2026, 9, 25), cells.get("C15"));
        assertEquals("INE123A07012", cells.get("C16"));
        assertEquals(new BigDecimal("100.00"), cells.get("C17"));
        assertEquals("TEST BOND 2027", cells.get("C18"));
        assertEquals(LocalDate.of(2027, 8, 23), cells.get("C19"));
        assertEquals("23rd Of Every Month", cells.get("C20"));
        assertEquals(LocalDate.of(2026, 7, 23), cells.get("C21"));
        assertEquals(new BigDecimal("0.137000"), cells.get("C22"));
        assertEquals(64L, cells.get("C23"));
        assertEquals(11L, cells.get("C24"));
    }

    @Test
    void writesTheMoneyIntoSeparateCellsRatherThanOneTotal() {

        Map<String, Object> cells = cellMap.toCells(values());

        /*
         * Each of these cells holds a formula in the template, using day-count
         * conventions that disagree with this application's. Overwriting them
         * one by one is what stops the template's own arithmetic from winning.
         */
        assertEquals(new BigDecimal("1100.00"), cells.get("C25"));
        assertEquals(new BigDecimal("1100.00"), cells.get("C26"));
        assertEquals(new BigDecimal("26.41"), cells.get("C27"));
        assertEquals(new BigDecimal("0.00"), cells.get("C28"));
        assertEquals(new BigDecimal("1126.41"), cells.get("C29"));
    }

    @Test
    void putsOurParticularsInTheFourCellBlock() {

        Map<String, Object> cells = cellMap.toCells(values());

        /*
         * Rows 30 to 34 hold two label/value pairs: A/B on the left, C/D on the
         * right. Only the B and D values are written — the A and C labels are the
         * template's wording and are left alone.
         */
        assertEquals("AAHCA7743E", cells.get("B30"));
        assertEquals("IN619994", cells.get("B31"));
        assertEquals("ICLL0000001", cells.get("D31"));
        assertEquals("Indian Clearing Corporation Ltd", cells.get("B32"));
        assertEquals("All Time Securities pvt. Ltd.", cells.get("D32"));
        assertEquals("ICDM(T+0)", cells.get("B33"));
        assertEquals("ICCL Or RBI", cells.get("D34"));
    }

    @Test
    void writesNothingOverALabelCell() {

        Map<String, Object> cells = cellMap.toCells(values());

        /*
         * Column A and the right-hand label column C are the letter's fixed
         * wording. Writing a value there would replace "Maturty  Date" with a
         * date and leave the page unreadable.
         */
        for (String address : new String[] {
                "A12", "A13", "A14", "A15", "A16", "A17", "A18", "A19", "A20",
                "A21", "A22", "A23", "A24", "A25", "A26", "A27", "A28", "A29",
                "A30", "A31", "A32", "A33", "A34",
                "C30", "C31", "C32", "C33", "C34" }) {

            assertFalse(
                    cells.containsKey(address),
                    address + " is a label cell and must not be written to");
        }
    }

    @Test
    void leavesTheIdentifiersThisApplicationDoesNotHold() {

        Map<String, Object> cells = cellMap.toCells(values());

        /*
         * "Your PAN" is a customer-side identifier and this application has no
         * source for it — User carries only a panStatus, not a number. It is not
         * in the map at all, so the cell keeps whatever the template has (empty).
         * Writing a configured default here would put a PAN we cannot vouch for
         * on a legal document.
         */
        assertFalse(
                cells.containsKey("D30"),
                "the customer's PAN has no source and must be left alone");

        /*
         * Account number and settlement number are the same story: the clearing
         * house issues settlement numbers per deal, and no account number is
         * held anywhere. They are written as blanks, which clears whatever
         * sample value the template was saved with.
         */
        assertEquals("", cells.get("D33"));
        assertEquals("", cells.get("B34"));
    }

    @Test
    void overwritesTheTemplatesStaleEchoOfTheCounterpartyLine() {

        Map<String, Object> cells = cellMap.toCells(values());

        /*
         * C39 holds a live formula echoing A7. Left alone it would reproduce
         * whichever counterparty the template was last saved with — a different
         * customer's name on this customer's letter.
         */
        assertEquals("Counterparty Name- Test Customer", cells.get("A7"));
        assertEquals("Counterparty Name- Test Customer", cells.get("C39"));
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private DealConfirmationSheetValues values() {

        return new DealConfirmationSheetValues(
                LocalDate.of(2026, 9, 25),
                "DC-20260925-000001",
                "Counterparty Name- Test Customer",
                "Our Sale",
                "ICCL",
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 25),
                "INE123A07012",
                new BigDecimal("100.00"),
                "TEST BOND 2027",
                LocalDate.of(2027, 8, 23),
                "23rd Of Every Month",
                LocalDate.of(2026, 7, 23),
                new BigDecimal("0.137000"),
                64L,
                11L,
                new BigDecimal("1100.00"),
                new BigDecimal("1100.00"),
                new BigDecimal("26.41"),
                new BigDecimal("0.00"),
                new BigDecimal("1126.41"));
    }
}
