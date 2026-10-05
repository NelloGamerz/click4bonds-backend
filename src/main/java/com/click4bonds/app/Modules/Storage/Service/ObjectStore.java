package com.click4bonds.app.Modules.Storage.Service;

import java.net.URI;
import java.time.Duration;

import com.click4bonds.app.Modules.Storage.Model.StoredObject;

/**
 * Stores bytes somewhere durable and gives them back.
 *
 * <p>The shared half of the application's file handling: any feature that
 * produces a file to keep — a generated letter, an uploaded document, an
 * export — stores it here rather than inventing its own arrangement. Its
 * counterpart in the document module, {@code DocumentStorage}, is a thinner,
 * deal-shaped view over this: it knows about storage keys derived from deal
 * references and nothing else.</p>
 *
 * <p><strong>Keys belong to the caller.</strong> This store applies no prefix and
 * no schema, so a caller chooses a layout that suits it and remains responsible
 * for deriving it deterministically. That is what makes one store serve several
 * features without a policy that would have to be the union of all of them.</p>
 *
 * <p>Implementations are expected to be thread-safe: a Spring singleton is
 * injected into request-handling beans.</p>
 */
public interface ObjectStore {

    /**
     * Writes an object, replacing any object already stored under this key.
     *
     * <p>Replacement rather than rejection is required, not incidental:
     * regenerating a file after a failure must land on the same key, or every
     * retry would leave another orphan behind.</p>
     *
     * @param key         where to store it, relative and chosen by the caller
     * @param content     the bytes
     * @param contentType MIME type to store them as
     * @return where it was stored
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the object could not be written
     */
    StoredObject put(String key, byte[] content, String contentType);

    /**
     * Reads an object back.
     *
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when there is no readable object under this key
     */
    byte[] get(String key);

    /**
     * @return true when an object exists under this key
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the store could not be reached to ask
     */
    boolean exists(String key);

    /**
     * Removes an object. Removing one that is not there succeeds.
     *
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the object could not be removed
     */
    void delete(String key);

    /**
     * The durable address of an object: its endpoint, bucket and key.
     *
     * <p>A name, not a grant. It does not expire and it carries no signature, so
     * against a private bucket it is only useful to something that can already
     * authenticate — see {@link #presignedGetUrl} for the other kind.</p>
     *
     * <p>Exists so a caller can record <em>where an object went</em> in a form a
     * human can read and paste into a browser without having to reconstruct it
     * from configuration. The cost is real and worth stating: the address pins
     * the endpoint that built it, so moving to a different account or fronting
     * the bucket with a custom domain leaves recorded addresses pointing at a
     * host that is no longer used. Use it where that visibility is worth the
     * coupling, and keep the key where it is not.</p>
     *
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when no endpoint or bucket is configured to build one from
     */
    URI urlFor(String key);

    /**
     * The key an address refers to — the inverse of {@link #urlFor}.
     *
     * <p>Accepts a bare relative key as well as an address, because a caller may
     * have recorded either: a key is what this store worked in before addresses
     * existed, and rows written then still hold one. Refusing them would make
     * those rows unreadable for no gain.</p>
     *
     * @param location an address from {@link #urlFor}, or a bare relative key
     * @return the key to pass to {@link #get}
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the value is neither, or names a different endpoint or bucket
     *         than the one configured — which is a mismatch between data and
     *         configuration, and answering it with a key from the wrong bucket
     *         would be worse than failing
     */
    String keyFor(String location);

    /**
     * A URL that grants temporary read access to one object.
     *
     * <p>The reason to keep objects in a bucket rather than on a disk: a caller
     * that only needs to hand a file to a browser can redirect to this instead of
     * streaming the bytes through the application, leaving the bucket itself
     * private.</p>
     *
     * @param key key of the object to grant access to
     * @param ttl how long the URL stays valid
     * @return the signed URL
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the URL could not be signed
     */
    URI presignedGetUrl(String key, Duration ttl);
}
