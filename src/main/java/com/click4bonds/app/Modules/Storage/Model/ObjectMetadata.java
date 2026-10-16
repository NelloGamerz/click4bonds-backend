package com.click4bonds.app.Modules.Storage.Model;

import java.time.Instant;

/**
 * What the store holds under a key, as the store itself reports it.
 *
 * <p>Distinct from {@link StoredObject}, and the distinction is not cosmetic.
 * {@code StoredObject} is what a caller <em>wrote</em> — echoed back from the
 * arguments it passed, so a caller that was wrong about the content type stays
 * wrong. This is what the bucket <em>has</em>: read back from the service, so it
 * is the only value that can contradict a caller.</p>
 *
 * <p>That is what makes it usable as a check. An upload goes straight from a
 * client to the bucket, past this application, so the size and content type
 * actually stored are the provider's word rather than ours — and confirming an
 * upload means reading them here and comparing.</p>
 *
 * @param key          the key it was read from
 * @param contentType  MIME type the object is stored as, as the provider reports
 *                     it
 * @param size         size in bytes. Boxed into a {@code Long} by the provider's
 *                     own reply, and reported as {@code 0} rather than a null
 *                     when it is absent
 * @param etag         the provider's entity tag, which changes when the object
 *                     changes. Useful to a caller that wants to know whether
 *                     what it is looking at is the same bytes it saw before
 * @param lastModified when the object was last written, as the provider reports
 *                     it
 */
public record ObjectMetadata(
        String key,
        String contentType,
        long size,
        String etag,
        Instant lastModified
) {
}
