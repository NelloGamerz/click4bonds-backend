package com.click4bonds.app.Modules.Storage.Model;

/**
 * An object that has been persisted, and where to find it again.
 *
 * @param key         the key it was stored under, exactly as the caller supplied
 *                    it. Returned rather than left to the caller to remember, so
 *                    the thing that was persisted and the thing that gets
 *                    recorded are the same value.
 * @param contentType MIME type it was stored with
 * @param size        size in bytes
 */
public record StoredObject(
        String key,
        String contentType,
        long size
) {
}
