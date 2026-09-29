package com.click4bonds.app.Modules.Document.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Document.Exception.DocumentStorageException;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;
import com.click4bonds.app.Modules.Storage.Exception.StorageException;
import com.click4bonds.app.Modules.Storage.Model.StoredObject;
import com.click4bonds.app.Modules.Storage.Service.ObjectStore;

/**
 * The boundary between the document module and the shared object store.
 *
 * <p>Two things are worth pinning here, and neither is the delegation itself.
 * First, that the key recorded on a deal is the key the object is stored under —
 * an adapter that quietly prefixed one but not the other would write objects
 * nothing could find again. Second, that a storage failure arrives as a
 * {@link DocumentStorageException}: {@code DealConfirmationService} treats that
 * type as "the letter could not be kept" and logs it without failing a purchase
 * that has already been committed, and it would not recognise the store's own
 * exception.</p>
 *
 * <p>No bucket is involved. The store is an interface, so the mapping is
 * exercised against a mock.</p>
 */
@ExtendWith(MockitoExtension.class)
class R2DocumentStorageTest {

    private static final String KEY = "2026/09/DC-20260929-000001.pdf";
    private static final String LOCATION =
            "https://abc123.r2.cloudflarestorage.com/click4bonds-documents/" + KEY;
    private static final String FILE_NAME = "DC-20260929-000001.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final byte[] CONTENT = "letter".getBytes(StandardCharsets.UTF_8);

    @Mock
    private ObjectStore objectStore;

    private R2DocumentStorage storage() {
        return new R2DocumentStorage(objectStore);
    }

    @Test
    void storesUnderTheKeyItReports() {

        when(objectStore.put(KEY, CONTENT, CONTENT_TYPE))
                .thenReturn(new StoredObject(KEY, CONTENT_TYPE, CONTENT.length));

        StoredDocument stored = storage().store(KEY, FILE_NAME, CONTENT_TYPE, CONTENT);

        verify(objectStore).put(KEY, CONTENT, CONTENT_TYPE);

        /*
         * The reported key must be the one written. If this ever diverges, a deal
         * row records a location its document was never stored at.
         */
        assertThat(stored.storageKey()).isEqualTo(KEY);
        assertThat(stored.fileName()).isEqualTo(FILE_NAME);
        assertThat(stored.contentType()).isEqualTo(CONTENT_TYPE);
        assertThat(stored.size()).isEqualTo(CONTENT.length);
    }

    @Test
    void readsTheStoredBytes() {

        when(objectStore.get(KEY)).thenReturn(CONTENT);

        assertThat(storage().read(KEY)).isEqualTo(CONTENT);
    }

    @Test
    void reportsExistenceFromTheStore() {

        when(objectStore.exists(KEY)).thenReturn(true);

        assertThat(storage().exists(KEY)).isTrue();
    }

    @Test
    void reportsAbsenceFromTheStore() {

        when(objectStore.exists(KEY)).thenReturn(false);

        assertThat(storage().exists(KEY)).isFalse();
    }

    @Test
    void translatesAWriteFailure() {

        StorageException failure = new StorageException("R2 said no");

        doThrow(failure).when(objectStore).put(anyString(), any(), anyString());

        assertThatThrownBy(() -> storage().store(KEY, FILE_NAME, CONTENT_TYPE, CONTENT))
                .isInstanceOf(DocumentStorageException.class)
                .hasCause(failure);
    }

    @Test
    void translatesAReadFailure() {

        StorageException failure = new StorageException("R2 said no");

        when(objectStore.get(KEY)).thenThrow(failure);

        assertThatThrownBy(() -> storage().read(KEY))
                .isInstanceOf(DocumentStorageException.class)
                .hasCause(failure);
    }

    @Test
    void translatesAnExistenceCheckFailure() {

        StorageException failure = new StorageException("R2 said no");

        when(objectStore.exists(KEY)).thenThrow(failure);

        /*
         * An unreachable bucket must not be reported as "the document is not
         * there" — a caller would act on that by regenerating a letter that may
         * well exist.
         */
        assertThatThrownBy(() -> storage().exists(KEY))
                .isInstanceOf(DocumentStorageException.class)
                .hasCause(failure);
    }

    /* ------------------------------------------------------------------ *
     * Keys and addresses
     * ------------------------------------------------------------------ */

    @Test
    void buildsAnAddressFromAKey() {

        when(objectStore.urlFor(KEY)).thenReturn(URI.create(LOCATION));

        /*
         * The deal row gets the address; the bucket gets the key. This is the
         * conversion on the way in, and it returns a String because that is what
         * a deal row can hold.
         */
        assertThat(storage().locationOf(KEY)).isEqualTo(LOCATION);
    }

    @Test
    void resolvesAnAddressBackToAKey() {

        when(objectStore.keyFor(LOCATION)).thenReturn(KEY);

        assertThat(storage().keyOf(LOCATION)).isEqualTo(KEY);
    }

    @Test
    void translatesAnUnaddressableKey() {

        StorageException failure = new StorageException("no endpoint configured");

        when(objectStore.urlFor(KEY)).thenThrow(failure);

        assertThatThrownBy(() -> storage().locationOf(KEY))
                .isInstanceOf(DocumentStorageException.class)
                .hasCause(failure);
    }

    @Test
    void translatesAnUnresolvableAddress() {

        StorageException failure = new StorageException("was not written by this endpoint");

        when(objectStore.keyFor(LOCATION)).thenThrow(failure);

        assertThatThrownBy(() -> storage().keyOf(LOCATION))
                .isInstanceOf(DocumentStorageException.class)
                .hasCause(failure);
    }
}
