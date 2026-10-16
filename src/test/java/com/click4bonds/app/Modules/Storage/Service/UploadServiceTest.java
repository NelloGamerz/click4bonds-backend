package com.click4bonds.app.Modules.Storage.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import com.click4bonds.app.Modules.Storage.Config.StorageProperties;
import com.click4bonds.app.Modules.Storage.Exception.StorageException;
import com.click4bonds.app.Modules.Storage.Exception.UploadRejectedException;
import com.click4bonds.app.Modules.Storage.Model.ObjectMetadata;

/**
 * The upload policy: what may be uploaded, where it lands, and what happens when
 * what arrives is not what was authorised.
 *
 * <p>A unit test with a mocked store, because the interesting behaviour is
 * entirely this class's — the store is asked to sign a URL and does so. What a
 * live bucket would add is whether R2 accepts the signature, which
 * {@code R2ObjectStoreTest} covers where a bucket is configured.</p>
 *
 * <p>Most of these tests are about refusals rather than successes, which is the
 * right balance for a control whose purpose is to keep things out: a policy that
 * accepts what it should is unremarkable, and one that accepts what it should
 * not is the whole risk.</p>
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    private static final String PDF = "application/pdf";
    private static final String PNG = "image/png";

    private static final URI SIGNED = URI.create(
            "https://abc123.r2.cloudflarestorage.com/bucket/signed?X-Amz-Signature=abc");

    @Mock
    private ObjectStore objectStore;

    private StorageProperties properties;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {

        properties = new StorageProperties();

        uploadService = new UploadService(objectStore, properties);
    }

    /* ------------------------------------------------------------------ *
     * Content types
     * ------------------------------------------------------------------ */

    @Nested
    class ContentTypes {

        @Test
        void acceptsAnAllowedTypeAndSignsItNormalised() {

            when(objectStore.presignedPutUrl(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(SIGNED);

            UploadService.PresignedUpload upload =
                    uploadService.presignUpload("kyc", PDF);

            /*
             * The normalised value is what is signed, not what was passed in.
             * Signing the raw string would bake a client's spelling into the
             * URL, and the content type is one of the few things the store
             * enforces — so it has to be the canonical form.
             */
            assertThat(upload.contentType()).isEqualTo(PDF);

            verify(objectStore).presignedPutUrl(anyString(), eq(PDF), any(Duration.class));
        }

        @Test
        void ignoresParametersAndCase() {

            assertThat(UploadService.normalizeContentType("application/pdf; charset=binary"))
                    .isEqualTo(PDF);

            assertThat(UploadService.normalizeContentType("  IMAGE/PNG  "))
                    .isEqualTo(PNG);
        }

        @Test
        void refusesATypeOutsideTheAllowList() {

            /*
             * Refused before anything is signed, so the bucket never accepts it
             * — the allow-list is enforced at the point of authorisation rather
             * than by inspecting what arrives.
             */
            assertThatThrownBy(() -> uploadService.presignUpload("kyc", "application/zip"))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("application/zip");

            verifyNoInteractions(objectStore);
        }

        @Test
        void refusesEverythingWhenTheAllowListIsEmpty() {

            /*
             * Failing closed. An absent allow-list is the one configuration
             * where the safe reading and the convenient one differ, and this is
             * a control whose job is to keep things out.
             */
            properties.getR2().setAllowedContentTypes(new LinkedHashSet<>());

            assertThatThrownBy(() -> uploadService.presignUpload("kyc", PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verifyNoInteractions(objectStore);
        }

        @Test
        void refusesABlankType() {

            assertThatThrownBy(() -> uploadService.presignUpload("kyc", "  "))
                    .isInstanceOf(UploadRejectedException.class);

            assertThatThrownBy(() -> uploadService.presignUpload("kyc", null))
                    .isInstanceOf(UploadRejectedException.class);
        }

        @Test
        void refusesAnAllowedTypeItCannotName() {

            /*
             * A type the operator allowed but the module has no extension for.
             * Refused rather than stored extension-less: the key is what finds
             * the object later, and one with no suffix is an object nobody can
             * open. The message names the property so the operator can act.
             */
            properties.getR2().getAllowedContentTypes().add("application/x-custom");

            assertThatThrownBy(() -> uploadService.presignUpload("kyc", "application/x-custom"))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("allowed-content-types");

            verifyNoInteractions(objectStore);
        }
    }

    /* ------------------------------------------------------------------ *
     * Keys
     * ------------------------------------------------------------------ */

    @Nested
    class Keys {

        @Test
        void derivesAKeyUnderThePrefixWithTheTypesExtension() {

            when(objectStore.presignedPutUrl(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(SIGNED);

            UploadService.PresignedUpload pdf = uploadService.presignUpload("kyc", PDF);
            UploadService.PresignedUpload png = uploadService.presignUpload("kyc", PNG);

            assertThat(pdf.key()).startsWith("kyc/").endsWith(".pdf");
            assertThat(png.key()).endsWith(".png");

            /*
             * Filed by the month it arrived, so an operator looking for what was
             * uploaded in a given month has something to list.
             */
            assertThat(pdf.key()).matches("kyc/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");
        }

        @Test
        void doesNotPutTwoUploadsUnderTheSameKey() {

            /*
             * The property that stops one client overwriting another's file, and
             * the reason keys are derived rather than supplied.
             */
            when(objectStore.presignedPutUrl(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(SIGNED);

            assertThat(uploadService.presignUpload("kyc", PDF).key())
                    .isNotEqualTo(uploadService.presignUpload("kyc", PDF).key());
        }

        @Test
        void refusesAPrefixThatIsNotAPlainRelativePath() {

            /*
             * A prefix comes from calling code, not from a request, so a bad one
             * is a mistake in this repository — reported as
             * IllegalArgumentException rather than as a rejected upload, which
             * would send a client looking for a problem in a value they never
             * supplied.
             */
            for (String prefix : new String[] {
                    "/absolute", "trailing/", "..", ".", "kyc/..", "kyc/../admin",
                    "kyc//docs", "kyc docs", "kyc\\docs", ""}) {

                assertThatThrownBy(() -> uploadService.presignUpload(prefix, PDF))
                        .as("prefix %s", prefix)
                        .isInstanceOf(IllegalArgumentException.class);
            }

            verifyNoInteractions(objectStore);
        }
    }

    /* ------------------------------------------------------------------ *
     * Lifetimes and limits come from configuration
     * ------------------------------------------------------------------ */

    @Test
    void usesTheConfiguredUploadLifetimeAndReportsTheLimit() {

        properties.getR2().setPresignedUploadTtl(Duration.ofMinutes(3));
        properties.getR2().setMaxUploadSize(DataSize.ofMegabytes(4));

        when(objectStore.presignedPutUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn(SIGNED);

        Instant before = Instant.now();

        UploadService.PresignedUpload upload = uploadService.presignUpload("kyc", PDF);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);

        verify(objectStore).presignedPutUrl(anyString(), anyString(), ttl.capture());

        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(3));

        assertThat(upload.expiresAt())
                .isAfterOrEqualTo(before.plus(Duration.ofMinutes(3)));

        /*
         * Sent so a client can refuse an oversized file before spending a slow
         * connection on it. It is not what enforces the limit — see
         * confirmUpload — and the two must not be confused.
         */
        assertThat(upload.maxSize()).isEqualTo(4L * 1024 * 1024);
    }

    @Test
    void usesTheConfiguredDownloadLifetime() {

        properties.getR2().setPresignedDownloadTtl(Duration.ofSeconds(90));

        when(objectStore.presignedGetUrl(anyString(), any(Duration.class))).thenReturn(SIGNED);

        uploadService.presignDownload("kyc/2026/10/abc.pdf");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);

        verify(objectStore).presignedGetUrl(anyString(), ttl.capture());

        assertThat(ttl.getValue()).isEqualTo(Duration.ofSeconds(90));
    }

    /* ------------------------------------------------------------------ *
     * Confirmation
     * ------------------------------------------------------------------ */

    @Nested
    class Confirmation {

        private static final String KEY = "kyc/2026/10/abc.pdf";

        @Test
        void acceptsAnUploadThatMatchesWhatWasAuthorised() {

            ObjectMetadata metadata = new ObjectMetadata(
                    KEY, PDF, 2048L, "etag", Instant.now());

            when(objectStore.stat(KEY)).thenReturn(Optional.of(metadata));

            assertThat(uploadService.confirmUpload(KEY, PDF)).isEqualTo(metadata);

            verify(objectStore, never()).delete(anyString());
        }

        @Test
        void refusesWhenNothingWasUploaded() {

            /*
             * An ordinary outcome, not an attack: a dropped connection, a closed
             * browser, a file that failed to read. Reported as the client's
             * problem because nothing reaches this point until the client says
             * it has finished.
             */
            when(objectStore.stat(KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> uploadService.confirmUpload(KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verify(objectStore, never()).delete(anyString());
        }

        @Test
        void refusesAnUploadOverTheLimitAndRemovesIt() {

            /*
             * The only place the size limit can be applied: a signed PUT cannot
             * bind the length of its body, so the store accepts an oversized
             * object and this is what refuses it.
             *
             * The deletion is the point of the test. Leaving the object would
             * mean the bucket accumulates every oversized file a client ever
             * sent while the application reports that it accepted none — and the
             * object would stay readable to anyone holding its URL.
             */
            properties.getR2().setMaxUploadSize(DataSize.ofMegabytes(1));

            when(objectStore.stat(KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(KEY, PDF, DataSize.ofMegabytes(2).toBytes(), "etag", Instant.now())));

            assertThatThrownBy(() -> uploadService.confirmUpload(KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("over the");

            verify(objectStore).delete(KEY);
        }

        @Test
        void acceptsAnUploadExactlyOnTheLimit() {

            /*
             * "Over the limit" is over it. A file of exactly the configured size
             * is within the policy, and an off-by-one here would refuse a file
             * the operator said was acceptable.
             */
            properties.getR2().setMaxUploadSize(DataSize.ofMegabytes(1));

            long exactly = DataSize.ofMegabytes(1).toBytes();

            when(objectStore.stat(KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(KEY, PDF, exactly, "etag", Instant.now())));

            assertThat(uploadService.confirmUpload(KEY, PDF).size()).isEqualTo(exactly);

            verify(objectStore, never()).delete(anyString());
        }

        @Test
        void refusesAnUploadThatIsNotTheTypeItWasAuthorisedAs() {

            /*
             * Should be impossible — the content type is signed into the URL, so
             * the store rejects a mismatch — but checked anyway. A disagreement
             * means something other than this flow wrote the object, and that is
             * worth failing on rather than recording.
             */
            when(objectStore.stat(KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(KEY, PNG, 100L, "etag", Instant.now())));

            assertThatThrownBy(() -> uploadService.confirmUpload(KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verify(objectStore).delete(KEY);
        }

        @Test
        void refusesATypeThatWasAllowedWhenSignedAndIsNotNow() {

            /*
             * Configuration can move between the presign and the confirmation.
             * An object stored under a type that is no longer permitted should
             * not survive on the strength of having been permitted once.
             */
            when(objectStore.stat(KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(KEY, PDF, 100L, "etag", Instant.now())));

            properties.getR2().setAllowedContentTypes(new LinkedHashSet<>());

            assertThatThrownBy(() -> uploadService.confirmUpload(KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verify(objectStore).delete(KEY);
        }

        @Test
        void reportsTheRejectionRatherThanACleanupFailure() {

            /*
             * If removing the rejected object also fails, the caller still needs
             * to be told their upload was refused. An object left in the bucket
             * is an operator's problem, reported in the log — not an answer that
             * replaces the one the client can act on.
             */
            properties.getR2().setMaxUploadSize(DataSize.ofMegabytes(1));

            when(objectStore.stat(KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(KEY, PDF, DataSize.ofMegabytes(2).toBytes(), "etag", Instant.now())));

            org.mockito.Mockito.doThrow(new StorageException("bucket unreachable"))
                    .when(objectStore).delete(KEY);

            assertThatThrownBy(() -> uploadService.confirmUpload(KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("over the");
        }

        @Test
        void treatsAMismatchedContentTypeCaseInsensitively() {

            /*
             * The stored value is compared in the same normalised form the
             * allow-list is written in, so a provider that reports a type in
             * different case does not read as a different format.
             */
            when(objectStore.stat(KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(KEY, "Application/PDF", 100L, "etag", Instant.now())));

            assertThat(uploadService.confirmUpload(KEY, PDF).size()).isEqualTo(100L);

            verify(objectStore, never()).delete(anyString());
        }
    }
}
