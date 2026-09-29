package com.click4bonds.app.Modules.Storage.Exception;

/**
 * An object could not be written to, read from, or removed from the object
 * store.
 *
 * <p>Deliberately one type for every operation and every cause — a missing
 * object, a rejected credential, an unreachable endpoint. Callers of this module
 * are storing bytes they have already produced, and the useful distinction at
 * that point is "it worked" versus "it did not"; the provider's own error message
 * is carried as the cause for an operator to read. A module used by several
 * features should not make each of them enumerate an SDK's exception tree.</p>
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
