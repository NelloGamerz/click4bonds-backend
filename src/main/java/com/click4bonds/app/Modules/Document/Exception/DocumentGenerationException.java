package com.click4bonds.app.Modules.Document.Exception;

/**
 * A document could not be produced.
 *
 * <p>Thrown for every reason a document fails to come into existence: the
 * template is missing or unreadable, a cell could not be written, the PDF engine
 * is unavailable, or it ran but produced nothing.</p>
 *
 * <p><strong>Callers must not let this fail the work the document describes.</strong>
 * For a deal confirmation the purchase is already committed and its inventory
 * already reserved by the time this is thrown, so the deal stands and is left
 * for a later retry — see {@code DealConfirmationService.generateDocument}.</p>
 */
public class DocumentGenerationException extends RuntimeException {

    public DocumentGenerationException(String message) {
        super(message);
    }

    public DocumentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
