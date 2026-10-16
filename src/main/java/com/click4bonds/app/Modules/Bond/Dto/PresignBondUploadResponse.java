package com.click4bonds.app.Modules.Bond.Dto;

import java.time.Instant;

/**
 * A URL an admin may upload one bond file to, and the terms of that upload.
 *
 * <p>The URL is signed and short-lived, and the client sends the file straight
 * to the object store with it — the bytes never pass through this application.
 * What this application keeps is the key, which the client reports back when it
 * has finished so the file can be checked and recorded.</p>
 *
 * <p>The storage key is decided here, not by the caller, and is derived from
 * the bond and a random identifier. That is what stops one upload from naming
 * its way over another bond's file, and what makes the object unreadable to
 * anyone who was not given the URL.</p>
 *
 * @param storageKey  where the file will wait until it is confirmed, not where
 *                    it will live: confirming is what files it under its
 *                    permanent key, and the confirmation answers with that key.
 *                    Report this one back unchanged — it names the upload, it is
 *                    not a reference to keep
 * @param uploadUrl   the signed URL to {@code PUT} the file to
 * @param contentType the type the upload is authorised as. <strong>Send it as
 *                    the {@code Content-Type} header</strong>, exactly as
 *                    given: it is signed into the URL, and the object store
 *                    rejects a request that sends a different one
 * @param expiresAt   when the URL stops working. After this the upload must be
 *                    started again from a fresh presign; the bytes sent against
 *                    an expired URL are refused
 * @param maxSize     the largest body that will be accepted, in bytes. A client
 *                    should check its own file against this before sending it,
 *                    because the limit is enforced after the upload completes —
 *                    see {@code UploadService}
 */
public record PresignBondUploadResponse(
        String storageKey,
        String uploadUrl,
        String contentType,
        Instant expiresAt,
        long maxSize
) {
}
