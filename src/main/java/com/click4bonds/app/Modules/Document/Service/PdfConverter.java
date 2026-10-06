package com.click4bonds.app.Modules.Document.Service;

/**
 * Renders a spreadsheet as a PDF.
 *
 * <p>An interface because this is the one part of the module that depends on an
 * external program. Everything else — the template fill, the storage — can be
 * exercised without it, and keeping it behind a seam is what lets the test suite
 * run on a machine with no LibreOffice installed.</p>
 */
public interface PdfConverter {

    /**
     * Converts a spreadsheet to PDF.
     *
     * @param xlsx     the spreadsheet bytes
     * @param baseName a filename stem for the intermediate file. Must not contain
     *                 path separators or characters the engine would mangle; the
     *                 output name is derived from it.
     * @return the PDF bytes, never empty
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException
     *         when the engine is missing, times out, or produces nothing
     */
    byte[] toPdf(byte[] xlsx, String baseName);
}
