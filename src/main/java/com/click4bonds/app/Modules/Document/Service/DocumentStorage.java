package com.click4bonds.app.Modules.Document.Service;

import com.click4bonds.app.Modules.Document.Model.StoredDocument;

/**
 * Keeps generated documents somewhere durable.
 *
 * <p>Bytes in, a location out. Keys are relative and opaque to the caller, which
 * is what lets the backing store change — a filesystem today, an object store
 * later — without touching a single deal row: the caller persists the key, not a
 * path.</p>
 */
public interface DocumentStorage {

    /**
     * Writes a document, replacing any document already under this key.
     *
     * <p>Replacement rather than rejection is required, not incidental:
     * regenerating a deal's letter after a failure must land on the same key, or
     * every retry would leave another orphaned file behind.</p>
     *
     * @param storageKey relative key, as produced by {@link DocumentKeys}
     * @param fileName   name to offer a user downloading it
     * @param contentType MIME type
     * @param content    the bytes
     * @return where it was stored
     */
    StoredDocument store(String storageKey, String fileName, String contentType, byte[] content);

    /**
     * Reads a stored document back.
     *
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentStorageException
     *         when the key does not resolve to a readable document
     */
    byte[] read(String storageKey);

    /** @return true when a document exists under this key */
    boolean exists(String storageKey);
}
