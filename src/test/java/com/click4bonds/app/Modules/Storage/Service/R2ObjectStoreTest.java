package com.click4bonds.app.Modules.Storage.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Storage.Config.StorageProperties;
import com.click4bonds.app.Modules.Storage.Exception.StorageException;
import com.click4bonds.app.Modules.Storage.Model.StoredObject;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * The R2 store's configuration and its mapping onto S3 calls.
 *
 * <p>Written without a bucket, because the parts most likely to be wrong are not
 * the parts a bucket would exercise. The settings asserted here are the ones that
 * decide whether R2 accepts a request at all — path-style addressing and chunked
 * encoding — and both fail in ways that look like a credential problem: a 403
 * signature mismatch, or a bucket hostname that does not resolve. Neither is
 * visible in a mocked client's arguments, so they are asserted on the
 * configuration object itself.</p>
 *
 * <p>The mapping tests use a mocked {@link S3Client}. They pin which bucket, key
 * and content type an operation is translated into, which is what a live bucket
 * would otherwise be the only way to check.</p>
 */
@ExtendWith(MockitoExtension.class)
class R2ObjectStoreTest {

    private static final String KEY = "2026/09/DC-20260929-000001.pdf";
    private static final String BUCKET = "click4bonds-documents";
    private static final String ENDPOINT = "https://abc123.r2.cloudflarestorage.com";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final byte[] CONTENT = "letter".getBytes(StandardCharsets.UTF_8);

    @Mock
    private S3Client client;

    private final List<R2ObjectStore> created = new ArrayList<>();

    /* ------------------------------------------------------------------ *
     * Configuration that decides whether R2 accepts a request at all
     * ------------------------------------------------------------------ */

    @Test
    void addressesTheBucketInThePath() {

        /*
         * R2 serves objects from the account endpoint with the bucket in the
         * path. The client's default moves the bucket into the hostname, which
         * does not resolve — and reads as a DNS or TLS problem rather than a
         * configuration one.
         */
        assertThat(R2ObjectStore.r2ServiceConfiguration().pathStyleAccessEnabled()).isTrue();
    }

    @Test
    void doesNotUseChunkedTransferEncoding() {

        /*
         * The SDK's default aws-chunked upload produces a signature R2 rejects
         * with a 403, which reads as though the credentials were wrong.
         */
        assertThat(R2ObjectStore.r2ServiceConfiguration().chunkedEncodingEnabled()).isFalse();
    }

    @Test
    void constructsWithNothingConfigured() {

        /*
         * The guarantee a context-load test depends on. Document generation can
         * be switched off, and a laptop or CI machine has no bucket at all;
         * neither may be unable to start.
         */
        R2ObjectStore store = new R2ObjectStore(new StorageProperties());

        assertThat(store).isNotNull();

        store.close();
    }

    /* ------------------------------------------------------------------ *
     * Misconfiguration, reported by name
     * ------------------------------------------------------------------ */

    @Test
    void namesTheMissingBucket() {

        StorageProperties properties = configured();
        properties.getR2().setBucket("");

        assertThatThrownBy(() -> store(properties).get(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("storage.r2.bucket");
    }

    @Test
    void namesTheMissingEndpoint() {

        StorageProperties properties = configured();
        properties.getR2().setEndpoint("");

        assertThatThrownBy(() -> store(properties).get(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("storage.r2.endpoint");
    }

    /* ------------------------------------------------------------------ *
     * Keys
     * ------------------------------------------------------------------ */

    @Test
    void rejectsABlankKey() {

        assertThatThrownBy(() -> store(configured()).get("   "))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void rejectsAnAbsoluteKey() {

        /*
         * A leading slash means the caller built a path, not an object key —
         * the object would be stored under a key nothing else agrees on, which
         * is worse than a rejected write because it looks like it succeeded.
         */
        assertThatThrownBy(() -> store(configured()).get("/2026/09/letter.pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void rejectsAParentSegment() {

        assertThatThrownBy(() -> store(configured()).get("2026/09/../../letter.pdf"))
                .isInstanceOf(StorageException.class);
    }

    /* ------------------------------------------------------------------ *
     * Mapping onto S3 calls
     * ------------------------------------------------------------------ */

    @Test
    void putsTheKeyBucketAndContentType() {

        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StoredObject stored = store(configured()).put(KEY, CONTENT, CONTENT_TYPE);

        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(client).putObject(request.capture(), any(RequestBody.class));

        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().contentType()).isEqualTo(CONTENT_TYPE);

        assertThat(stored.key()).isEqualTo(KEY);
        assertThat(stored.contentType()).isEqualTo(CONTENT_TYPE);
        assertThat(stored.size()).isEqualTo(CONTENT.length);
    }

    @Test
    void readsTheObjectBack() throws Exception {

        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> stream =
                mock(ResponseInputStream.class);

        when(stream.readAllBytes()).thenReturn(CONTENT);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        assertThat(store(configured()).get(KEY)).isEqualTo(CONTENT);
    }

    @Test
    void findsAnExistingObject() {

        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(store(configured()).exists(KEY)).isTrue();
    }

    @Test
    void reportsAMissingObjectAsAbsent() {

        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThat(store(configured()).exists(KEY)).isFalse();
    }

    @Test
    void reportsAFailedExistenceCheckAsAFailure() {

        /*
         * Not as "absent". A caller that reads an unreachable bucket as "the
         * document is not there" will regenerate a document that may well exist,
         * and — with a deterministic key — overwrite it.
         */
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        assertThatThrownBy(() -> store(configured()).exists(KEY))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void refusesANonPositivePresignedLifetime() {

        assertThatThrownBy(() -> store(configured()).presignedGetUrl(KEY, Duration.ZERO))
                .isInstanceOf(StorageException.class);
    }

    /* ------------------------------------------------------------------ *
     * Addresses
     * ------------------------------------------------------------------ */

    @Test
    void buildsAPathStyleAddress() {

        URI address = store(configured()).urlFor(KEY);

        /*
         * Path style, matching how the client addresses the bucket. An address
         * built the other way — bucket as a subdomain — would not be the URL the
         * object is actually served at.
         */
        assertThat(address.toString())
                .isEqualTo(ENDPOINT + "/" + BUCKET + "/" + KEY);
    }

    @Test
    void roundTripsAnAddressBackToItsKey() {

        R2ObjectStore store = store(configured());

        assertThat(store.keyFor(store.urlFor(KEY).toString())).isEqualTo(KEY);
    }

    @Test
    void acceptsABareKeyAsALocation() {

        /*
         * Rows written before addresses were recorded hold a bare key. Refusing
         * them would strand those documents for no gain, so the store reads both.
         */
        assertThat(store(configured()).keyFor(KEY)).isEqualTo(KEY);
    }

    @Test
    void refusesAnAddressFromAnotherEndpoint() {

        /*
         * A data-versus-configuration mismatch. Reading the key out of it anyway
         * would fetch from a bucket the address does not point at and look like a
         * success, so this fails instead.
         */
        assertThatThrownBy(() -> store(configured())
                .keyFor("https://someone-elses-account.r2.cloudflarestorage.com/"
                        + BUCKET + "/" + KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void refusesAnAddressFromAnotherBucket() {

        assertThatThrownBy(() -> store(configured())
                .keyFor(ENDPOINT + "/someone-elses-bucket/" + KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void refusesABlankLocation() {

        assertThatThrownBy(() -> store(configured()).keyFor("  "))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void refusesToAddressANonPositiveOrEscapingKey() {

        assertThatThrownBy(() -> store(configured()).urlFor("/absolute.pdf"))
                .isInstanceOf(StorageException.class);

        assertThatThrownBy(() -> store(configured()).urlFor("2026/../escape.pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void toleratesATrailingSlashOnTheEndpoint() {

        /*
         * An operator writing the endpoint with a trailing slash must not get
         * "...//bucket/key" recorded on every deal row.
         */
        StorageProperties properties = configured();
        properties.getR2().setEndpoint(ENDPOINT + "/");

        assertThat(store(properties).urlFor(KEY).toString())
                .isEqualTo(ENDPOINT + "/" + BUCKET + "/" + KEY);
    }

    @Test
    void refusesAnEndpointThatIsNotAnAbsoluteUrl() {

        StorageProperties properties = configured();
        properties.getR2().setEndpoint("abc123.r2.cloudflarestorage.com");

        assertThatThrownBy(() -> store(properties).urlFor(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("storage.r2.endpoint");
    }

    /* ------------------------------------------------------------------ *
     * Against a real bucket, where one is configured
     * ------------------------------------------------------------------ */

    @Test
    void roundTripsAgainstALiveBucket() {

        /*
         * The only check that exercises the checksum and chunked-encoding
         * settings end to end: a mocked client accepts anything. Skipped wherever
         * R2 is not configured, which is every CI run, so this is documentation
         * that runs on the machine where it can.
         */
        assumeTrue(
                hasText(System.getenv("R2_ENDPOINT"))
                        && hasText(System.getenv("R2_BUCKET"))
                        && hasText(System.getenv("R2_ACCESS_KEY_ID"))
                        && hasText(System.getenv("R2_SECRET_ACCESS_KEY")),
                "no R2 bucket configured");

        StorageProperties properties = new StorageProperties();

        properties.getR2().setEndpoint(System.getenv("R2_ENDPOINT"));
        properties.getR2().setBucket(System.getenv("R2_BUCKET"));
        properties.getR2().setAccessKeyId(System.getenv("R2_ACCESS_KEY_ID"));
        properties.getR2().setSecretAccessKey(System.getenv("R2_SECRET_ACCESS_KEY"));

        R2ObjectStore live = new R2ObjectStore(properties);

        String key = "test/" + java.util.UUID.randomUUID() + ".txt";

        try {

            live.put(key, CONTENT, "text/plain");

            assertThat(live.exists(key)).isTrue();
            assertThat(live.get(key)).isEqualTo(CONTENT);

        } finally {

            live.delete(key);
            live.close();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private R2ObjectStore store(StorageProperties properties) {

        /*
         * Registered for closing after the test. Only the client is mocked; the
         * store builds a real presigner either way, and one holds an HTTP
         * connection pool per instance.
         */
        R2ObjectStore store = new R2ObjectStore(properties, client);

        created.add(store);

        return store;
    }

    @AfterEach
    void closeCreatedStores() {

        created.forEach(R2ObjectStore::close);
        created.clear();
    }

    private StorageProperties configured() {

        StorageProperties properties = new StorageProperties();

        properties.getR2().setEndpoint(ENDPOINT);
        properties.getR2().setBucket(BUCKET);
        properties.getR2().setAccessKeyId("key");
        properties.getR2().setSecretAccessKey("secret");

        return properties;
    }
}
