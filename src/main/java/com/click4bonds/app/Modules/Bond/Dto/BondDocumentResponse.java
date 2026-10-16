package com.click4bonds.app.Modules.Bond.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;

/**
 * One document attached to a bond.
 *
 * <p>Everything a caller needs to list it and fetch it, and nothing about where
 * it physically lives. The object key is deliberately not here: it is an
 * implementation detail of this application's storage, it would be the same
 * value as every other bond's key but for a random segment, and a client has no
 * use for it when {@link #downloadUrl} is what it actually wants.</p>
 *
 * @param id           the document's identifier, for {@link #downloadUrl} and
 *                     for deleting it
 * @param documentType what it is
 * @param fileName     the name to offer a person downloading it
 * @param contentType  MIME type it is stored and served as
 * @param sizeBytes    size in bytes, as the object store reported it
 * @param uploadedAt   when it was confirmed
 * @param downloadUrl  where to fetch it, relative to the API root. Stable,
 *                     unlike the signed URL it leads to
 */
public record BondDocumentResponse(
        UUID id,
        BondDocumentType documentType,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        String downloadUrl
) {
}
