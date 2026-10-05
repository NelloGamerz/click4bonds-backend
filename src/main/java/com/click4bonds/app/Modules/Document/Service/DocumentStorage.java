package com.click4bonds.app.Modules.Document.Service;

import com.click4bonds.app.Modules.Document.Model.StoredDocument;

/**
 * Keeps generated documents somewhere durable.
 *
 * <p>Bytes in, a location out. Keys are relative and opaque to the caller, which
 * is what lets the backing store change without touching a single deal row: the
 * caller persists the key, not a path. That is not hypothetical — documents
 * began on a filesystem and now live in Cloudflare R2, and no deal row, key or
 * caller changed when they moved.</p>
 *
 * <p>The only implementation is {@link R2DocumentStorage}, which hands the bytes
 * to the application's shared
 * {@link com.click4bonds.app.Modules.Storage.Service.ObjectStore}. This interface
 * stays narrow and deal-shaped on purpose: the general store knows about keys and
 * content types, not about deals.</p>
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

    /**
     * The address to record for a stored document.
     *
     * <p>Distinct from the key, and the distinction is the point. A key is this
     * module's internal way of naming a document and is meaningless without the
     * store's configuration; an address is something a person can read, paste
     * into a browser and act on. Callers that persist a document for someone else
     * to find — a deal row, an audit trail — record the address; callers that
     * want the bytes back go through {@link #keyOf} first.</p>
     *
     * @param storageKey key a document was stored under
     * @return the address to persist
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentStorageException
     *         when an address cannot be built — in practice, when the store is
     *         not configured
     */
    String locationOf(String storageKey);

    /**
     * The key a recorded address refers to.
     *
     * <p>Accepts a bare relative key too, because rows written before addresses
     * were recorded hold one. See
     * {@link com.click4bonds.app.Modules.Storage.Service.ObjectStore#keyFor}.</p>
     *
     * @param location an address from {@link #locationOf}, or a bare relative key
     * @return the key to pass to {@link #read}
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentStorageException
     *         when the value is neither, or names a different endpoint or bucket
     *         than the one configured
     */
    String keyOf(String location);
}
