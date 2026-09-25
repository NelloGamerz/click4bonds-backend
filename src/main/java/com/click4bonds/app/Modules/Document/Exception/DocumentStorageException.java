package com.click4bonds.app.Modules.Document.Exception;

/**
 * A generated document could not be written to storage, or read back from it.
 *
 * <p>Separate from {@link DocumentGenerationException} because the two failures
 * have different causes and different responses: a generation failure means the
 * bytes were never produced, while a storage failure means they were produced and
 * then lost. Both leave the same trail — the caller records nothing and the work
 * stays retryable — but only this one is worth alerting on as an infrastructure
 * problem.</p>
 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message) {
        super(message);
    }

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
