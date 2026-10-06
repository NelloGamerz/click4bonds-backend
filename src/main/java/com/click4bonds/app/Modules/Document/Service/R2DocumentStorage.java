package com.click4bonds.app.Modules.Document.Service;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Document.Exception.DocumentStorageException;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;
import com.click4bonds.app.Modules.Storage.Exception.StorageException;
import com.click4bonds.app.Modules.Storage.Service.ObjectStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps generated documents in the shared object store.
 *
 * <p>The deal side of the application knows documents by a storage key derived
 * from a deal reference and nothing else, so this is where that view meets the
 * general-purpose store: it translates a document key into an object key —
 * which, because keys are the caller's to choose, is the same string — and
 * translates the store's failures back into the document module's.</p>
 *
 * <p>The translation is not decoration. {@code DealConfirmationService} treats a
 * {@link DocumentStorageException} as "the letter could not be kept" and logs it
 * without failing the purchase, while the download endpoint lets it propagate.
 * An {@link StorageException} escaping to those callers would be a type they do
 * not know, so it is converted at the boundary rather than at each call site.</p>
 *
 * <p><strong>Keys are used verbatim, with no prefix.</strong> The key a document
 * is stored under is exactly the key it is read back with, so a row can be
 * matched to an object by eye. A prefix would make the key the document module
 * derived and the key the bucket holds disagree, and every historical row would
 * silently depend on the prefix never changing.</p>
 *
 * <p><strong>A key and an address are both strings, and neither is the other.</strong>
 * This is the one place they are converted, so no other caller has to know that
 * {@code 2026/09/DC-20260929-000001.pdf} and
 * {@code https://account.r2.cloudflarestorage.com/bucket/2026/09/DC-20260929-000001.pdf}
 * name the same object. A deal row holds the address because a person reads it; a
 * read takes the key because that is what the bucket is asked for.</p>
 *
 * <p><strong>Replacement, not rejection.</strong> The store overwrites on write,
 * which the document pipeline depends on: regenerating a deal's letter has to
 * land on the same key, or every retry would leave another orphan behind.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class R2DocumentStorage implements DocumentStorage {

    private final ObjectStore objectStore;

    @Override
    public StoredDocument store(
            String storageKey,
            String fileName,
            String contentType,
            byte[] content) {

        try {

            objectStore.put(storageKey, content, contentType);

        } catch (StorageException failure) {

            throw new DocumentStorageException(
                    "Failed to store document at key " + storageKey, failure);
        }

        /*
         * Built from the arguments rather than the store's reply: what a caller
         * needs back is the name to offer a user, which the store has no notion
         * of — it knows keys and content types, not the file name a browser
         * should save.
         */
        return new StoredDocument(storageKey, fileName, contentType, content.length);
    }

    @Override
    public byte[] read(String storageKey) {

        try {

            return objectStore.get(storageKey);

        } catch (StorageException failure) {

            throw new DocumentStorageException(
                    "Failed to read document at key " + storageKey, failure);
        }
    }

    @Override
    public boolean exists(String storageKey) {

        try {

            return objectStore.exists(storageKey);

        } catch (StorageException failure) {

            throw new DocumentStorageException(
                    "Failed to check for document at key " + storageKey, failure);
        }
    }

    @Override
    public String locationOf(String storageKey) {

        try {

            return objectStore.urlFor(storageKey).toString();

        } catch (StorageException failure) {

            throw new DocumentStorageException(
                    "Failed to build an address for document at key " + storageKey, failure);
        }
    }

    @Override
    public String keyOf(String location) {

        try {

            return objectStore.keyFor(location);

        } catch (StorageException failure) {

            throw new DocumentStorageException(
                    "Failed to resolve the key for document at " + location, failure);
        }
    }
}
