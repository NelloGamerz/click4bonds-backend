package com.click4bonds.app.Modules.DealConfirmation.Dto;

/**
 * A generated deal confirmation document.
 *
 * <p>Deliberately format-agnostic: today the module produces nothing, later it
 * will hold a filled XLSX and then a PDF. Nothing here names a file format, a
 * library or a storage location, so swapping the producer does not touch the
 * deal creation code.</p>
 *
 * @param fileName    suggested file name, or null when there is no document
 * @param contentType MIME type of {@code content}, or null when there is none
 * @param content     the bytes themselves, or null when there is none
 */
public record DealConfirmationDocument(
        String fileName,
        String contentType,
        byte[] content
) {

    /**
     * "No document was produced."
     *
     * <p>Returned by the placeholder implementation while the Excel/PDF step is
     * unimplemented. A deal is still a valid deal without a document, so this is
     * a normal outcome and not an error.</p>
     */
    public static DealConfirmationDocument none() {
        return new DealConfirmationDocument(null, null, null);
    }

    /** @return true when this carries actual bytes */
    public boolean isPresent() {
        return content != null && content.length > 0;
    }
}
