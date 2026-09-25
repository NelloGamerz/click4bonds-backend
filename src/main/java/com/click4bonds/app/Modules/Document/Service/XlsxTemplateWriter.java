package com.click4bonds.app.Modules.Document.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fills a spreadsheet template and returns the result.
 *
 * <p>The template is a real customer-facing document, so this class is
 * deliberately narrow: it writes the cells it is told to write and changes
 * nothing else. Styles, column widths, merged regions, the letterhead drawing
 * and the legal text all survive untouched, because the {@link CellStyle} is
 * captured from the existing cell and re-applied to the replacement.</p>
 *
 * <p><strong>Every other sheet is removed.</strong> A PDF conversion renders the
 * whole workbook, so leaving the sibling sheets in would staple the purchase-side
 * letterheads — and their own sample data — onto the customer's confirmation.
 * The template's other sheets exist as a library of layouts, not as content.</p>
 *
 * <p><strong>Formulas are replaced, not recalculated.</strong> The template's
 * money cells carry their own arithmetic, which disagrees with this
 * application's (different day-count conventions). Callers that have computed a
 * value themselves overwrite the formula outright, so the printed document can
 * never disagree with what the platform charged.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XlsxTemplateWriter {

    private final DocumentProperties properties;

    /**
     * Fills the configured template.
     *
     * @param sheetName sheet to fill; matched with surrounding whitespace
     *                  ignored, because every sheet in this workbook is named
     *                  with a trailing space
     * @param cells     cell address ({@code "C14"}) to value. A null or blank
     *                  value clears the cell
     * @return the filled workbook
     * @throws DocumentGenerationException when the template is missing, the
     *                                     sheet does not exist, or a value
     *                                     cannot be written
     */
    public byte[] fill(String sheetName, Map<String, Object> cells) {

        String templatePath = properties.getTemplate().getPath();

        try (InputStream template = open(templatePath);
                Workbook workbook = new XSSFWorkbook(template)) {

            Sheet sheet = requireSheet(workbook, sheetName);

            keepOnly(workbook, sheet);

            writeAll(workbook, sheet, cells);

            return toBytes(workbook);

        } catch (IOException failure) {

            throw new DocumentGenerationException(
                    "Failed to fill template " + templatePath, failure);

        } catch (IllegalArgumentException malformed) {

            throw new DocumentGenerationException(
                    "Failed to fill template " + templatePath
                            + ": " + malformed.getMessage(), malformed);
        }
    }

    /**
     * Opens the template from the classpath.
     *
     * <p>A stream, never a file: the template is packaged inside the application
     * jar, where it has no filesystem path, and its name contains a space.</p>
     */
    private InputStream open(String templatePath) {

        ClassPathResource resource = new ClassPathResource(templatePath);

        if (!resource.exists()) {
            throw new DocumentGenerationException(
                    "Document template not found on the classpath: " + templatePath);
        }

        try {

            return resource.getInputStream();

        } catch (IOException failure) {

            throw new DocumentGenerationException(
                    "Document template could not be opened: " + templatePath, failure);
        }
    }

    /**
     * Finds the sheet to fill, ignoring surrounding whitespace.
     *
     * <p>Every sheet in this workbook is named with a trailing space
     * ({@code "PSU Private Sale "}), which is invisible in Excel and in YAML.
     * An exact lookup returns null and would silently fill nothing, so this
     * trims both sides and fails loudly when nothing matches.</p>
     */
    private Sheet requireSheet(Workbook workbook, String sheetName) {

        String wanted = sheetName == null ? "" : sheetName.trim();

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {

            if (workbook.getSheetName(i).trim().equalsIgnoreCase(wanted)) {
                return workbook.getSheetAt(i);
            }
        }

        StringBuilder available = new StringBuilder();

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {

            available.append(i > 0 ? ", " : "")
                    .append('[').append(workbook.getSheetName(i)).append(']');
        }

        throw new DocumentGenerationException(
                "Sheet \"" + sheetName + "\" not found in the document template."
                        + " Available: " + available);
    }

    /**
     * Discards every sheet but the one being filled.
     *
     * <p>Removed from the end backwards so the indices of the sheets still to be
     * inspected do not shift as earlier ones disappear.</p>
     */
    private void keepOnly(Workbook workbook, Sheet keep) {

        String keepName = keep.getSheetName();

        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {

            if (!workbook.getSheetName(i).equals(keepName)) {
                workbook.removeSheetAt(i);
            }
        }

        int index = workbook.getSheetIndex(keep);

        workbook.setActiveSheet(index);
        workbook.setSelectedTab(index);
    }

    private void writeAll(Workbook workbook, Sheet sheet, Map<String, Object> cells) {

        if (cells == null || cells.isEmpty()) {

            log.warn("No cells were supplied for sheet {}", sheet.getSheetName());

            return;
        }

        for (Map.Entry<String, Object> entry : cells.entrySet()) {

            write(workbook, sheet, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Writes one cell, preserving the style it already had.
     *
     * <p>The cell is removed and re-created rather than assigned to. Setting a
     * value on a cell that currently holds a formula leaves the formula in place
     * on some POI code paths, which would let the template's own arithmetic win
     * over the value the caller supplied — exactly the outcome overwriting these
     * cells is meant to prevent. Re-creating the cell removes any formula and any
     * stale cached result unambiguously.</p>
     */
    private void write(Workbook workbook, Sheet sheet, String address, Object value) {

        CellReference reference;

        try {

            reference = new CellReference(address);

        } catch (RuntimeException malformed) {

            throw new DocumentGenerationException(
                    "Not a valid cell address: " + address, malformed);
        }

        Row row = sheet.getRow(reference.getRow());

        if (row == null) {
            row = sheet.createRow(reference.getRow());
        }

        Cell existing = row.getCell(reference.getCol());
        CellStyle style = existing == null ? null : existing.getCellStyle();

        if (existing != null) {
            row.removeCell(existing);
        }

        Cell cell = row.createCell(reference.getCol());

        if (style != null) {
            cell.setCellStyle(style);
        }

        applyValue(workbook, cell, address, value, style);
    }

    private void applyValue(
            Workbook workbook,
            Cell cell,
            String address,
            Object value,
            CellStyle style) {

        if (value == null) {
            return;
        }

        if (value instanceof String text) {

            if (!text.isBlank()) {
                cell.setCellValue(text);
            }

            return;
        }

        if (value instanceof LocalDate date) {

            if (style == null) {
                /*
                 * No style to inherit and POI refuses a date without a date
                 * format. Only reachable on a template that left the cell
                 * unstyled; the template in the repository styles all of them.
                 */
                cell.setCellStyle(dateStyle(workbook));
            }

            cell.setCellValue(date);

            return;
        }

        if (value instanceof BigDecimal amount) {

            /*
             * XLSX stores numbers as doubles, so a BigDecimal has to be reduced
             * before it gets there. Rounding here rather than letting the
             * conversion truncate keeps the printed figure and the stored figure
             * from differing in the last paisa.
             */
            cell.setCellValue(amount.setScale(2, RoundingMode.HALF_UP).doubleValue());

            return;
        }

        if (value instanceof Number number) {

            cell.setCellValue(number.doubleValue());

            return;
        }

        if (value instanceof Boolean flag) {

            cell.setCellValue(flag);

            return;
        }

        throw new DocumentGenerationException(
                "Unsupported value for cell " + address + ": " + value.getClass().getName());
    }

    private CellStyle dateStyle(Workbook workbook) {

        CreationHelper helper = workbook.getCreationHelper();

        CellStyle style = workbook.createCellStyle();

        style.setDataFormat(helper.createDataFormat().getFormat("dd-mm-yyyy"));

        return style;
    }

    private byte[] toBytes(Workbook workbook) throws IOException {

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            workbook.write(out);

            return out.toByteArray();
        }
    }
}
