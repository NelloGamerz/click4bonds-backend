package com.click4bonds.app.Modules.DealConfirmation.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;

/**
 * Draws the corporate deal confirmation letter as a PDF.
 *
 * <p>The letter is rendered from the values computed for the deal, exactly as
 * the bond cash-flow statement is: no spreadsheet is filled and no external
 * converter runs, so a purchase does not depend on LibreOffice being installed
 * on the server.</p>
 *
 * <p>Kept behind an interface for the same reason as
 * {@code BondCashFlowPdfService}: the layout is the only part of the module that
 * reaches for a rendering library, and the service that orchestrates document
 * generation should not have to carry it.</p>
 */
public interface DealConfirmationPdfService {

    /**
     * Renders one deal confirmation letter.
     *
     * @param values the letter's values, already computed and rounded
     * @return the PDF bytes, never empty
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException
     *         when the document cannot be rendered
     */
    byte[] render(DealConfirmationSheetValues values);
}
