package com.click4bonds.app.Modules.Document.Model;

/**
 * A document that has been persisted, and where to find it again.
 *
 * @param storageKey  location relative to the storage root, never absolute. Kept
 *                    relative so a deal row is not tied to one host's directory
 *                    layout; resolve it through the storage implementation.
 * @param fileName    the name to offer a user downloading it, e.g.
 *                    {@code DC-20260923-000001.pdf}
 * @param contentType MIME type
 * @param size        size in bytes
 */
public record StoredDocument(
        String storageKey,
        String fileName,
        String contentType,
        long size
) {
}
