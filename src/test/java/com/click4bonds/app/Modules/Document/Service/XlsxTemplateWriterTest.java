package com.click4bonds.app.Modules.Document.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

/**
 * Fills the real committed template and reads the result back.
 *
 * <p>These tests deliberately run against
 * {@code src/main/resources/deal_confirmation/ATSPL Deal Format.xlsx} rather than
 * a fixture. The template is a real customer-facing document that the business
 * edits by hand, and the failure this guards against is a template revision
 * silently moving a cell: every other test in this package would keep passing
 * while the letters went out wrong.</p>
 *
 * <p>Two assertions here are load-bearing and worth keeping even if the rest
 * churn:</p>
 *
 * <ul>
 *   <li><strong>No formula survives.</strong> The template's money cells carry
 *       their own arithmetic, using day-count conventions that disagree with
 *       this application's. A cell the fill missed would print the template's
 *       number instead of the platform's.</li>
 *   <li><strong>No sample data survives.</strong> The committed template is
 *       filled with a previous deal's values — another buyer's ISIN, security
 *       name and quantity. Any cell the fill misses would print a stranger's
 *       trade on a customer's confirmation letter.</li>
 * </ul>
 */
class XlsxTemplateWriterTest {

    private static final String SHEET = "PSU Private Sale ";

    /**
     * Sample values committed in the template. None of these may appear in a
     * generated document: they belong to whichever deal the template was last
     * saved from.
     */
    private static final String[] SAMPLE_DATA = {
            "2026/S/SEP/121",
            "INE926R07027",
            "13.00% Unifinz Capital India Ltd 2028",
            "01st  Of Every Month"
    };

    private final DocumentProperties properties = new DocumentProperties();

    private final XlsxTemplateWriter writer = new XlsxTemplateWriter(properties);

    // =========================================================
    // THE MAPPED CELLS
    // =========================================================

    @Test
    void writesEveryMappedCellSoNoTemplateValueCanShowThrough() throws Exception {

        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            Sheet sheet = onlySheet(workbook);

            assertEquals("DC-20260925-000001", text(sheet, "A5"));
            assertEquals("Counterparty Name- Test Customer", text(sheet, "A7"));

            assertEquals("Our Sale", text(sheet, "C12"));
            assertEquals("ICCL", text(sheet, "C13"));
            assertEquals("INE123A07012", text(sheet, "C16"));
            assertEquals("TEST BOND 2027", text(sheet, "C18"));
            assertEquals("23rd Of Every Month", text(sheet, "C20"));

            assertEquals(100.0, number(sheet, "C17"), 0.0001);
            assertEquals(0.137, number(sheet, "C22"), 0.0000001);
            assertEquals(64.0, number(sheet, "C23"), 0.0001);
            assertEquals(11.0, number(sheet, "C24"), 0.0001);
            assertEquals(1100.0, number(sheet, "C25"), 0.0001);
            assertEquals(1100.0, number(sheet, "C26"), 0.0001);
            assertEquals(26.41, number(sheet, "C27"), 0.0001);
            assertEquals(0.0, number(sheet, "C28"), 0.0001);
            assertEquals(1126.41, number(sheet, "C29"), 0.0001);

            assertEquals("AAHCA7743E", text(sheet, "B30"));
            assertEquals("IN619994", text(sheet, "B31"));
            assertEquals("ICLL0000001", text(sheet, "D31"));
        }
    }

    @Test
    void writesDatesAsDatesSoTheyDoNotPrintAsSerialNumbers() throws Exception {

        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            Sheet sheet = onlySheet(workbook);

            /*
             * A date written into a cell without a date format displays as
             * 46288, not as a date — and the PDF conversion would carry that
             * through to the customer. The template styles these cells, and the
             * fill must preserve that style.
             */
            for (String address : new String[] { "C14", "C15", "C19", "C21" }) {

                Cell cell = cell(sheet, address);

                assertEquals(
                        CellType.NUMERIC, cell.getCellType(),
                        address + " should hold a numeric date, not a string");

                assertTrue(
                        DateUtil.isCellDateFormatted(cell),
                        address + " should be date-formatted, or it prints as a serial number");
            }
        }
    }

    // =========================================================
    // WHAT MUST NOT SURVIVE
    // =========================================================

    @Test
    void leavesNoFormulaAnywhereSoTheTemplatesOwnArithmeticCannotWin() throws Exception {

        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            Sheet sheet = onlySheet(workbook);

            Map<String, String> remaining = new LinkedHashMap<>();

            for (Row row : sheet) {

                for (Cell cell : row) {

                    if (cell.getCellType() == CellType.FORMULA) {

                        remaining.put(
                                cell.getAddress().formatAsString(),
                                cell.getCellFormula());
                    }
                }
            }

            assertTrue(
                    remaining.isEmpty(),
                    "The template's own formulas survived the fill, so the printed"
                            + " figures would not be the ones the platform charged: "
                            + remaining);
        }
    }

    @Test
    void leavesNoSampleDataFromWhicheverDealTheTemplateWasSavedFrom() throws Exception {

        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            Sheet sheet = onlySheet(workbook);

            String everything = allText(sheet);

            for (String sample : SAMPLE_DATA) {

                assertFalse(
                        everything.contains(sample),
                        "Sample value \"" + sample + "\" from the template's own deal"
                                + " survived the fill and would print on a customer's"
                                + " confirmation letter");
            }

            /*
             * C39 held a live echo of the counterparty cell. It is overwritten
             * with a literal, so it must not still be reproducing A7 as a
             * formula — which the formula sweep above would already catch, but
             * this pins the cell specifically.
             */
            assertFalse(
                    "Counterparty Name-".equals(safeText(sheet, "C39")),
                    "C39 still echoes the template's counterparty line");
        }
    }

    @Test
    void keepsOnlyTheSheetBeingFilledSoThePdfCannotStapleOnOtherLayouts() throws Exception {

        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            /*
             * A PDF conversion renders the entire workbook. The three sibling
             * sheets are purchase-side layouts carrying their own sample data;
             * leaving them in would attach them to the customer's confirmation.
             */
            assertEquals(
                    1, workbook.getNumberOfSheets(),
                    "Only the filled sheet should remain, or the PDF will contain"
                            + " the template's other layouts too");

            assertEquals(SHEET, workbook.getSheetName(0));
            assertEquals(0, workbook.getActiveSheetIndex());
        }
    }

    // =========================================================
    // SHEET LOOKUP
    // =========================================================

    @Test
    void findsTheSheetWhenTheNameIsGivenWithoutItsTrailingSpace() throws Exception {

        /*
         * Excel stores every sheet in this workbook with a trailing space, which
         * is invisible in the sheet tab and stripped by YAML. An exact-match
         * lookup returns null and would silently fill nothing, so the lookup
         * trims both sides.
         */
        byte[] filled = writer.fill("PSU Private Sale", dealCells());

        try (Workbook workbook = open(filled)) {

            assertEquals("INE123A07012", text(onlySheet(workbook), "C16"));
        }
    }

    @Test
    void failsLoudlyWhenTheSheetIsNotInTheTemplate() {

        DocumentGenerationException failure = assertThrows(
                DocumentGenerationException.class,
                () -> writer.fill("No Such Sheet", dealCells()));

        assertTrue(
                failure.getMessage().contains("No Such Sheet"),
                "The error should name the sheet that was asked for");

        assertTrue(
                failure.getMessage().contains("PSU Private Sale"),
                "The error should list the sheets that do exist, so the next"
                        + " person can see the real name");
    }

    // =========================================================
    // VALUE HANDLING
    // =========================================================

    @Test
    void clearsACellWhenTheValueIsNullSoNothingStaleIsLeftBehind() throws Exception {

        Map<String, Object> cells = new LinkedHashMap<>();

        cells.put("C16", null);

        byte[] filled = writer.fill(SHEET, cells);

        try (Workbook workbook = open(filled)) {

            Cell cell = cell(onlySheet(workbook), "C16");

            assertEquals(
                    CellType.BLANK, cell.getCellType(),
                    "A null value must clear the cell, not leave the template's"
                            + " own value in place");
        }
    }

    @Test
    void inheritsTheTemplatesStyleRatherThanReplacingIt() throws Exception {

        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            Cell cell = cell(onlySheet(workbook), "C16");

            /*
             * The cell is re-created to shed any formula it held, so its style
             * has to be carried across explicitly. Losing it would strip the
             * borders and alignment the letter is laid out with.
             */
            assertTrue(
                    cell.getCellStyle() != null,
                    "The template's style should survive the fill");
        }
    }

    @Test
    void keepsTheNumberFormatsTheWrittenValuesAssume() throws Exception {

        /*
         * These formats are what make the values readable, and two of them are
         * what make the units correct:
         *
         *   C22 is 0.00%, so writing the coupon as a FRACTION (0.137) prints
         *   "13.70%" while writing the stored percentage (13.70) would print
         *   "1370.00%". That conversion is the whole reason
         *   DealConfirmationSheetValuesFactory divides by 100.
         *
         *   C14/C15/C19/C21 are date formats, so a date written without one
         *   prints as a serial number.
         *
         * If the business reformats one of these cells, the letter silently
         * starts showing the wrong thing rather than failing, so it is worth
         * asserting here where the reason is written down.
         */
        byte[] filled = writer.fill(SHEET, dealCells());

        try (Workbook workbook = open(filled)) {

            Sheet sheet = onlySheet(workbook);

            assertEquals("0.00%", format(sheet, "C22"), "the coupon cell must stay a percentage");
            assertEquals("[$-409]d/mmm/yy;@", format(sheet, "C14"), "the deal date must stay a date");

            for (String money : new String[] { "C26", "C27" }) {
                assertEquals("0.00", format(sheet, money), money + " should print to the paisa");
            }
        }
    }

    private String format(Sheet sheet, String address) {
        return cell(sheet, address).getCellStyle().getDataFormatString();
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    /** Values a deal would supply, covering every mapped cell. */
    private Map<String, Object> dealCells() {

        Map<String, Object> cells = new HashMap<>();

        cells.put("A4", LocalDate.of(2026, 9, 25));
        cells.put("A5", "DC-20260925-000001");
        cells.put("A7", "Counterparty Name- Test Customer");

        cells.put("C12", "Our Sale");
        cells.put("C13", "ICCL");
        cells.put("C14", LocalDate.of(2026, 9, 25));
        cells.put("C15", LocalDate.of(2026, 9, 25));
        cells.put("C16", "INE123A07012");
        cells.put("C17", 100);
        cells.put("C18", "TEST BOND 2027");
        cells.put("C19", LocalDate.of(2027, 8, 23));
        cells.put("C20", "23rd Of Every Month");
        cells.put("C21", LocalDate.of(2026, 7, 23));
        cells.put("C22", 0.137);
        cells.put("C23", 64);
        cells.put("C24", 11);
        cells.put("C25", 1100);
        cells.put("C26", 1100);
        cells.put("C27", new java.math.BigDecimal("26.41"));
        cells.put("C28", java.math.BigDecimal.ZERO);
        cells.put("C29", new java.math.BigDecimal("1126.41"));

        cells.put("B30", "AAHCA7743E");
        cells.put("B31", "IN619994");
        cells.put("D31", "ICLL0000001");
        cells.put("B32", "Indian Clearing Corporation Ltd");
        cells.put("D32", "All Time Securities pvt. Ltd.");
        cells.put("B33", "ICDM(T+0)");
        cells.put("D33", "");
        cells.put("B34", "");
        cells.put("D34", "ICCL Or RBI");

        cells.put("C39", "Counterparty Name- Test Customer");

        return cells;
    }

    private Workbook open(byte[] xlsx) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private Sheet onlySheet(Workbook workbook) {
        return workbook.getSheetAt(0);
    }

    private Cell cell(Sheet sheet, String address) {

        CellReference reference = new CellReference(address);
        Row row = sheet.getRow(reference.getRow());

        assertTrue(row != null, "No row for " + address);

        Cell cell = row.getCell(reference.getCol());

        assertTrue(cell != null, "No cell for " + address);

        return cell;
    }

    private String text(Sheet sheet, String address) {
        return cell(sheet, address).getStringCellValue();
    }

    private String safeText(Sheet sheet, String address) {

        Cell cell = cell(sheet, address);

        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "";
    }

    private double number(Sheet sheet, String address) {
        return cell(sheet, address).getNumericCellValue();
    }

    /** Concatenates every string cell and every formula, for sample-data sweeps. */
    private String allText(Sheet sheet) {

        StringBuilder everything = new StringBuilder();

        for (Row row : sheet) {

            for (Cell cell : row) {

                if (cell.getCellType() == CellType.STRING) {
                    everything.append(cell.getStringCellValue()).append('\n');

                } else if (cell.getCellType() == CellType.FORMULA) {
                    everything.append(cell.getCellFormula()).append('\n');
                }
            }
        }

        return everything.toString();
    }
}
