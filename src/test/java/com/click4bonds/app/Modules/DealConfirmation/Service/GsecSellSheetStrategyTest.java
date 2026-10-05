package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

/**
 * Pins the G-Sec letter's layout.
 *
 * <p>The same reasoning as {@code PsuPrivateSaleSheetStrategyTest}: the addresses
 * are the whole point, so they are asserted one by one. The label positions they
 * correspond to are asserted against the real workbook in
 * {@code XlsxTemplateWriterTest}, so a mismatch cannot survive between them.</p>
 *
 * <p>Two things here are specific to this sheet and easy to get wrong: it
 * <em>deducts</em> TDS where the corporate sheet <em>adds</em> stamp duty, and the
 * customer's own identifiers (Your PAN, Your Dp Name, DP ID, CLIENT ID) sit in
 * columns C and D and must be left for the customer.</p>
 */
class GsecSellSheetStrategyTest {

    private final DocumentProperties properties = new DocumentProperties();

    private final GsecSellSheetStrategy strategy = new GsecSellSheetStrategy(properties);

    @Test
    void fillsTheSheetTheConfigurationNames() {

        assertEquals(properties.getTemplate().getGsecSheetName(), strategy.sheetName());
    }

    @Test
    void writesTheDealTermsIntoTheLabelValueRows() {

        Map<String, Object> cells = strategy.toCells(values());

        /*
         * Rows 12 to 24 put the label in column A and the value in column C.
         */
        assertEquals("Our Sale", cells.get("C12"));
        assertEquals("Demat", cells.get("C13"));
        assertEquals(LocalDate.of(2026, 9, 25), cells.get("C14"));
        assertEquals(LocalDate.of(2026, 9, 25), cells.get("C15"));
        assertEquals("IN0020240035", cells.get("C16"));
        assertEquals(new BigDecimal("100.00"), cells.get("C17"));
        assertEquals("7.34% GOI 2064", cells.get("C18"));
        assertEquals(LocalDate.of(2064, 4, 22), cells.get("C19"));
        assertEquals("22/04-22/10", cells.get("C20"));
        assertEquals(LocalDate.of(2026, 4, 22), cells.get("C21"));

        /*
         * The days are the G-Sec sheet's own count, not the corporate figure the
         * same record also carries.
         */
        assertEquals(159L, cells.get("C23"));
        assertEquals(11L, cells.get("C24"));
    }

    @Test
    void writesTheCouponAsAFractionSoTheSheetsOwnPercentFormatPrintsItRight() {

        Map<String, Object> cells = strategy.toCells(values());

        /*
         * 0.0734, not 7.34. This sheet's coupon cell is formatted 0.00% — a format
         * for a fraction — and its own sample data holds 0.0734. The corporate
         * sheet writes the percentage and replaces the format instead; doing that
         * here would be a hundred-fold error if the cell were ever used in a
         * formula, and it would also print "7.34%" from a value the sheet reads as
         * 7.34 whole.
         */
        assertEquals(0.0734d, (Double) cells.get("C22"), 0.0000001);

        /*
         * A double rather than a BigDecimal, because XlsxTemplateWriter rounds
         * every BigDecimal to two decimals: 0.0734 would arrive as 0.07 and the
         * letter would read "7.00%". This asserts the type as well as the value,
         * since that rounding is the failure mode.
         */
        assertInstanceOf(Double.class, cells.get("C22"));
    }

    @Test
    void usesTheGsecAccruedInterestNotTheCorporateOne() {

        Map<String, Object> cells = strategy.toCells(values());

        /*
         * The record carries two accrued-interest figures for the same deal, and
         * they are deliberately different numbers in this fixture (25.00 against
         * 35.66). This cell takes the G-Sec sheet's rule — quantum x coupon x days
         * / 360 — and the corporate figure must never reach it.
         */
        assertEquals(new BigDecimal("35.66"), cells.get("C27"));

        assertNotEquals(
                values().accruedInterest(), cells.get("C27"),
                "the corporate accrued interest must not be printed on the G-Sec sheet");

        assertNotEquals(
                values().accruedDays(), cells.get("C23"),
                "the corporate accrued days must not be printed on the G-Sec sheet");
    }

    @Test
    void refusesADealWithNoMaturityOrNoCouponRatherThanPrintingBlanks() {

        /*
         * The values factory returns the G-Sec figures as null when a deal cannot
         * support them, because a perpetual bond is legitimate for the corporate
         * letter. A government security always has a maturity and a coupon, so the
         * absence is only a problem here — and a letter whose consideration is
         * built on an absent interest figure confirms nothing.
         */
        DealConfirmationSheetValues withoutMaturity = new DealConfirmationSheetValues(
                LocalDate.of(2026, 9, 25),
                "DC-20260925-000001",
                "Counterparty Name- Test Customer",
                "Our Sale",
                "Demat",
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 25),
                "IN0020240035",
                new BigDecimal("100.00"),
                "7.34% GOI 2064",
                null,
                "22/04-22/10",
                LocalDate.of(2026, 4, 22),
                new BigDecimal("7.34"),
                105L,
                11L,
                new BigDecimal("1100.00"),
                new BigDecimal("1100.00"),
                new BigDecimal("21.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1121.00"),
                null,
                null,
                null,
                null,
                null);

        DocumentGenerationException failure = assertThrows(
                DocumentGenerationException.class,
                () -> strategy.toCells(withoutMaturity));

        assertTrue(
                failure.getMessage().contains("DC-20260925-000001"),
                "the refusal should name the deal, got: " + failure.getMessage());
    }

    @Test
    void printsDematRatherThanTheCorporateLettersClearingRoute() {

        /*
         * A G-Sec settles in demat form. Sharing the corporate letter's
         * mode-of-delivery would print ICCL here and name the wrong settlement
         * route on a government security.
         */
        Map<String, Object> cells = strategy.toCells(values());

        assertEquals("Demat", cells.get("C13"));
        assertFalse(
                "ICCL".equals(cells.get("C13")),
                "the G-Sec letter must not print the corporate settlement route");
    }

    @Test
    void deductsTdsFromTheConsiderationRatherThanAddingStampDuty() {

        Map<String, Object> cells = strategy.toCells(values());

        /*
         * The shape of the money is different from the corporate sheet: there is
         * no stamp-duty cell at all, the consideration is the sum before any
         * charge, and the total is that consideration MINUS the TDS.
         */
        assertEquals(new BigDecimal("1100.00"), cells.get("C25"));
        assertEquals(new BigDecimal("1100.00"), cells.get("C26"));

        // 1100.00 x 0.0734 x 159 / 360
        assertEquals(new BigDecimal("35.66"), cells.get("C27"));

        assertEquals(new BigDecimal("1135.66"), cells.get("C28"));

        // 10% of the accrued interest, not 0.1% of the consideration.
        assertEquals(new BigDecimal("3.57"), cells.get("C29"));

        assertEquals(new BigDecimal("1132.09"), cells.get("C30"));

        /*
         * The corporate sheet's stamp duty must not appear on this letter under
         * any address — it is a charge this instrument does not carry, and the
         * two sheets share one values record.
         */
        assertFalse(
                cells.containsValue(new BigDecimal("0.00")),
                "the corporate sheet's stamp duty must not be printed on the G-Sec letter");
    }

    @Test
    void writesOurParticularsButNotTheCustomersIdentifiers() {

        Map<String, Object> cells = strategy.toCells(values());

        assertEquals("All Time Securities pvt. Ltd.", cells.get("C31"));
        assertEquals("AAHCA7743E", cells.get("B32"));

        /*
         * Branch, bank, bank IFSC and account number are configured blank, and
         * written as blanks on purpose — that is what clears the sample values the
         * template was saved with. Omitting them would let the template's own
         * branch and bank details print on a customer's letter.
         */
        assertEquals("", cells.get("B33"));
        assertEquals("", cells.get("B34"));
        assertEquals("", cells.get("B35"));
        assertEquals("", cells.get("B36"));

        /*
         * D32:D35 hold the customer's PAN, demat account name, DP ID and client
         * ID. This application holds none of them; the letter leaves them blank
         * for the customer to complete.
         */
        for (String address : new String[] { "D32", "D33", "D34", "D35" }) {

            assertFalse(
                    cells.containsKey(address),
                    address + " is the customer's own identifier and must be left alone");
        }
    }

    @Test
    void writesNothingOverALabelCell() {

        Map<String, Object> cells = strategy.toCells(values());

        /*
         * Column A is the letter's fixed wording down both blocks, and column C
         * holds the labels of the customer's own block in rows 32-35. Writing a
         * value into any of them would replace wording with a figure.
         */
        for (String address : new String[] {
                "A12", "A13", "A14", "A15", "A16", "A17", "A18", "A19", "A20",
                "A21", "A22", "A23", "A24",
                "A25", "A26", "A27", "A28", "A29", "A30",
                "A31", "A32", "A33", "A34", "A35", "A36",
                "C32", "C33", "C34", "C35" }) {

            assertFalse(
                    cells.containsKey(address),
                    address + " is a label cell and must not be written to");
        }
    }

    @Test
    void overwritesTheTemplatesStaleEchoOfTheNameCell() {

        Map<String, Object> cells = strategy.toCells(values());

        /*
         * C41 holds a live formula echoing A7, which is the "Name" line. Left
         * alone it would reproduce whichever counterparty the template was last
         * saved with.
         */
        assertEquals("Counterparty Name- Test Customer", cells.get("A7"));
        assertEquals("Counterparty Name- Test Customer", cells.get("C41"));
    }

    @Test
    void overridesNoNumberFormats() {

        /*
         * The corporate sheet has to replace its coupon cell's format, because it
         * prints the rate as Bond stores it — a percentage — under a format meant
         * for a fraction. This sheet goes the other way: the coupon is written as a
         * fraction, which is what the cell's own 0.00% format expects. Nothing
         * needs overriding, and an override here would be a bug rather than a
         * harmless no-op.
         */
        assertEquals(Map.of(), strategy.numberFormats());
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    /**
     * A government security: 11 bonds of face value 100 at par, 159 accrued days
     * on the sheet's count and a 7.34% coupon, so the G-Sec accrued interest is
     * 35.66, the consideration 1135.66, TDS 3.57 and the total 1132.09.
     *
     * <p>The <em>corporate</em> accrued figures are set to different numbers on
     * purpose — 105 days, 25.00 interest — so a strategy that reached for the
     * wrong pair fails rather than coincidentally passing.</p>
     */
    private DealConfirmationSheetValues values() {

        return new DealConfirmationSheetValues(
                LocalDate.of(2026, 9, 25),
                "DC-20260925-000001",
                "Counterparty Name- Test Customer",
                "Our Sale",
                "Demat",
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 25),
                "IN0020240035",
                new BigDecimal("100.00"),
                "7.34% GOI 2064",
                LocalDate.of(2064, 4, 22),
                "22/04-22/10",
                LocalDate.of(2026, 4, 22),
                new BigDecimal("7.34"),
                105L,
                11L,
                new BigDecimal("1100.00"),
                new BigDecimal("1100.00"),
                new BigDecimal("25.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1125.00"),
                159L,
                new BigDecimal("35.66"),
                new BigDecimal("1135.66"),
                new BigDecimal("3.57"),
                new BigDecimal("1132.09"));
    }
}
