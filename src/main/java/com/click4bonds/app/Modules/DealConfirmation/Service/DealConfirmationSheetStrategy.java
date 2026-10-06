package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.Map;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;

/**
 * The layout of one deal confirmation letter: which sheet, and which cell each
 * printed value goes into.
 *
 * <p>Every sheet in the workbook is a different letter — a corporate bond sale
 * and a G-Sec sale do not print the same fields, and the G-Sec sheet charges TDS
 * where the corporate sheet charges stamp duty. Those differences are a whole
 * layout, so they are a strategy rather than a branch inside the fill.</p>
 *
 * <p><strong>The arithmetic is not here.</strong> All of it lives in
 * {@link DealConfirmationSheetValuesFactory}, which produces the same
 * {@link DealConfirmationSheetValues} for every strategy. A strategy decides
 * where a value is printed, never what it is — which is what keeps two sheets
 * from drifting into two definitions of accrued interest. A sheet that needs a
 * figure the shared values do not carry (the G-Sec sheet's TDS, for instance)
 * extends the values rather than recomputing anything.</p>
 *
 * <p>{@link DealConfirmationSheetStrategyFactory} decides which of these a given
 * deal gets; nothing else should name an implementation.</p>
 */
public interface DealConfirmationSheetStrategy {

    /**
     * The sheet of {@code document.template.path} this strategy fills.
     *
     * <p>Matched with surrounding whitespace ignored — every sheet in this
     * workbook is named with a trailing space, which is invisible in Excel and
     * stripped by YAML.</p>
     */
    String sheetName();

    /**
     * @param values the letter's values, already computed and rounded
     * @return cell address ({@code "C14"}) to value, in sheet order. Only values:
     *         the label cells are the template's wording and writing to one would
     *         leave the page unreadable
     */
    Map<String, Object> toCells(DealConfirmationSheetValues values);

    /**
     * The cells whose template number format does not fit the value written to
     * them.
     *
     * <p>Empty by default, which means every cell's own format is already right
     * for the value this application writes to it. Overriding one is for the
     * cells where the template's format suits the sample data it was saved with
     * but not the real value — a rate stored as a percentage written under a
     * format meant for a fraction, say. The replacement is format-only: the
     * cell's borders and alignment survive.</p>
     *
     * @return cell address to an Excel number format, for the addresses that need
     *         one
     */
    default Map<String, String> numberFormats() {

        return Map.of();
    }
}
