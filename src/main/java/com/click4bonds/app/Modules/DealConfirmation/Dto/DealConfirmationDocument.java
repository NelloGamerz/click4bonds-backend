package com.click4bonds.app.Modules.DealConfirmation.Dto;

/**
 * A generated deal confirmation document.
 *
 * <p>Deliberately format-agnostic: it names no file format and no library, so
 * swapping the producer does not touch the deal creation code.</p>
 *
 * <p>It does carry a {@code storageKey}, which the earlier version of this
 * record explicitly avoided. The reasoning changed once there was somewhere to
 * put a document: the caller has to persist <em>something</em> so the deal can
 * say whether it has a letter, and every alternative was worse. An absolute path
 * would tie a deal row to one host's directory layout, so moving the storage
 * root would leave every historical row pointing at nothing. A bare file name is
 * derivable from the deal reference and cannot tell the spreadsheet from the
 * PDF, so it records nothing. A storage key is relative, opaque to the caller,
 * and resolved by the storage implementation — the smallest honest thing.</p>
 *
 * @param fileName    suggested file name, or null when there is no document
 * @param contentType MIME type of {@code content}, or null when there is none
 * @param content     the bytes themselves, or null when there is none
 * @param storageKey  where the document was stored, relative to the storage
 *                    root, or null when nothing was stored
 */
public record DealConfirmationDocument(
        String fileName,
        String contentType,
        byte[] content,
        String storageKey
) {

    /**
     * "No document was produced."
     *
     * <p>Returned when the document step is switched off. A deal is still a
     * valid deal without a document, so this is a normal outcome and not an
     * error — a failure to produce one is signalled by throwing, not by
     * returning this.</p>
     */
    public static DealConfirmationDocument none() {
        return new DealConfirmationDocument(null, null, null, null);
    }

    /** @return true when this carries actual bytes */
    public boolean isPresent() {
        return content != null && content.length > 0;
    }
}
