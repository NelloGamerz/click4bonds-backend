package com.click4bonds.app.Modules.Storage.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
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

            assertThat(pdf.key()).startsWith("pending/kyc/").endsWith(".pdf");
            assertThat(png.key()).endsWith(".png");

            /*
             * Filed by the month it arrived, so an operator looking for what was
             * uploaded in a given month has something to list — and prefixed by
             * the marker, so what an operator lists for a month is also what
             * nothing has yet confirmed.
             */
            assertThat(pdf.key()).matches("pending/kyc/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");
        }

        @Test
        void issuesAPendingKeyThatIsNotWhereTheObjectWillLive() {

            /*
             * The property the whole confirmation rests on: what a client is
             * given write access to is not a key anything will reference. The
             * two differ by the marker and nothing else, so the folder, month,
             * identifier and extension a caller sees are carried across.
             */
            when(objectStore.presignedPutUrl(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(SIGNED);

            String key = uploadService.presignUpload("bonds/x/documents", PDF).key();

            assertThat(key).startsWith("pending/");
            assertThat(key.substring("pending/".length()))
                    .matches("bonds/x/documents/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");
        }

        @Test
        void refusesAPrefixThatBeginsWithTheReservedMarker() {

            /*
             * Otherwise a caller could create a folder whose contents are
             * indistinguishable from unconfirmed uploads — and whose permanent
             * keys would still begin with the marker, putting them back inside
             * the namespace the confirmation treats as removable. A folder
             * merely *named* pending further along is an ordinary name, so only
             * the first segment is refused.
             */
            for (String prefix : new String[] {"pending", "pending/kyc"}) {

                assertThatThrownBy(() -> uploadService.presignUpload(prefix, PDF))
                        .as("prefix %s", prefix)
                        .isInstanceOf(IllegalArgumentException.class);
            }

            when(objectStore.presignedPutUrl(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(SIGNED);

            assertThat(uploadService.presignUpload("kyc/pending", PDF).key())
                    .startsWith("pending/kyc/pending/");
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

        /**
         * A key shaped exactly as {@link UploadService#presignUpload} derives
         * one, so the tests exercise the shape a real upload has rather than a
         * convenient abbreviation of it.
         */
        private static final String PENDING_KEY =
                "pending/kyc/2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f.pdf";

        /** Where that upload lives once it has been confirmed. */
        private static final String PERMANENT_KEY =
                "kyc/2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f.pdf";

        /** An upload that has already been confirmed: a live, referenced object. */
        private static final String LIVE_KEY =
                "kyc/2026/09/8b1d0a72-4c33-4a51-b0e2-5d6f7a8b9c01.pdf";

        private static ObjectMetadata pending(long size) {
            return new ObjectMetadata(PENDING_KEY, PDF, size, "etag", Instant.now());
        }

        private static ObjectMetadata promoted(long size) {
            return new ObjectMetadata(PERMANENT_KEY, PDF, size, "etag", Instant.now());
        }

        @Test
        void acceptsAnUploadThatMatchesWhatWasAuthorised() {

            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(2048L)));
            when(objectStore.stat(PERMANENT_KEY)).thenReturn(Optional.of(promoted(2048L)));

            ObjectMetadata result = uploadService.confirmUpload(PENDING_KEY, PDF);

            /*
             * The key confirmed against is the one the URL was signed for, and
             * the key returned is the one the object now has. A caller recording
             * the former would be recording a key whose object this method has
             * just removed.
             */
            assertThat(result.key()).isEqualTo(PERMANENT_KEY);
            assertThat(result.size()).isEqualTo(2048L);

            InOrder order = inOrder(objectStore);

            order.verify(objectStore).copy(PENDING_KEY, PERMANENT_KEY, PDF);
            order.verify(objectStore).delete(PENDING_KEY);

            /* The destination was not overwritten, and nothing else was removed. */
            verify(objectStore, never()).delete(PERMANENT_KEY);
        }

        @Test
        void refusesWhenNothingWasUploaded() {

            /*
             * An ordinary outcome, not an attack: a dropped connection, a closed
             * browser, a file that failed to read. Reported as the client's
             * problem because nothing reaches this point until the client says
             * it has finished.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verify(objectStore, never()).delete(anyString());
            verify(objectStore, never()).copy(anyString(), anyString(), anyString());
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

            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(
                    pending(DataSize.ofMegabytes(2).toBytes())));

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("over the");

            verify(objectStore).delete(PENDING_KEY);
            verify(objectStore, never()).copy(anyString(), anyString(), anyString());
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

            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(exactly)));
            when(objectStore.stat(PERMANENT_KEY)).thenReturn(Optional.of(promoted(exactly)));

            assertThat(uploadService.confirmUpload(PENDING_KEY, PDF).size()).isEqualTo(exactly);
        }

        @Test
        void refusesAnUploadThatIsNotTheTypeItWasAuthorisedAs() {

            /*
             * Should be impossible — the content type is signed into the URL, so
             * the store rejects a mismatch — but checked anyway. A disagreement
             * means something other than this flow wrote the object, and that is
             * worth failing on rather than recording.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(PENDING_KEY, PNG, 100L, "etag", Instant.now())));

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verify(objectStore).delete(PENDING_KEY);
        }

        @Test
        void refusesATypeThatWasAllowedWhenSignedAndIsNotNow() {

            /*
             * Configuration can move between the presign and the confirmation.
             * An object stored under a type that is no longer permitted should
             * not survive on the strength of having been permitted once.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(100L)));

            properties.getR2().setAllowedContentTypes(new LinkedHashSet<>());

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class);

            verify(objectStore).delete(PENDING_KEY);
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

            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(
                    pending(DataSize.ofMegabytes(2).toBytes())));

            doThrow(new StorageException("bucket unreachable"))
                    .when(objectStore).delete(PENDING_KEY);

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
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
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(PENDING_KEY, "Application/PDF", 100L, "etag", Instant.now())));
            when(objectStore.stat(PERMANENT_KEY)).thenReturn(Optional.of(
                    new ObjectMetadata(PERMANENT_KEY, "Application/PDF", 100L, "etag", Instant.now())));

            assertThat(uploadService.confirmUpload(PENDING_KEY, PDF).size()).isEqualTo(100L);
        }

        /* -------------------------------------------------------------- *
         * A key that is not an unconfirmed upload this service issued
         * -------------------------------------------------------------- */

        @Test
        void refusesAKeyThatNamesAnExistingObjectAndNeverReachesTheStore() {

            /*
             * The regression this design exists for. A confirmation reports a
             * key back, a refusal removes the object at that key, and the key
             * was the client's to choose — so before this, confirming an
             * existing object with a content type it did not have was a way to
             * delete it: a document already recorded against a bond, a generated
             * letter, another upload.
             *
             * Nothing about that upload is even read now. There is no branch in
             * which a permanent object is examined and none in which it is
             * removed, so the refusal cannot depend on what the object happens
             * to be — which is what makes it hold for objects nobody has thought
             * of yet.
             */
            assertThatThrownBy(() -> uploadService.confirmUpload(LIVE_KEY, PNG))
                    .isInstanceOf(UploadRejectedException.class);

            verifyNoInteractions(objectStore);
        }

        @Test
        void refusesAKeyThatIsNotShapedLikeOneITCouldHaveDerived() {

            /*
             * "Starts with the marker" is not the test. Each of these is a value
             * a client could send, none is a key this service derives, and every
             * one of them names something that is not an unconfirmed upload: the
             * shape is checked so that a key cannot be constructed to look
             * pending — by naming a permanent object, by climbing out of the
             * namespace, or by ending in the marker itself.
             */
            for (String key : new String[] {
                    LIVE_KEY,
                    "kyc/2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f.pdf",
                    "pending/kyc/2026/10/not-a-uuid.pdf",
                    "pending/kyc/2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f",
                    "pending/../kyc/2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f.pdf",
                    "pending/kyc/./2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f.pdf",
                    "pending/pending/kyc/2026/10/3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f.pdf",
                    "pending/",
                    "pending",
                    "",
                    null}) {

                assertThatThrownBy(() -> uploadService.confirmUpload(key, PDF))
                        .as("key %s", key)
                        .isInstanceOf(UploadRejectedException.class);
            }

            verifyNoInteractions(objectStore);
        }

        @Test
        void acceptsOnlyTheKeysIssuedForThePrefixAsked() {

            /*
             * The other half of the eligibility rule, for a caller that has to
             * decide whether an upload is its own before confirming it — the
             * bond case. A permanent key is false however it is spelled, which
             * is what stops a caller's own rule from accepting something the
             * confirmation would then refuse.
             */
            assertThat(uploadService.isPendingKeyFor("kyc", PENDING_KEY)).isTrue();
            assertThat(uploadService.isPendingKeyFor("bonds/x/documents", PENDING_KEY)).isFalse();
            assertThat(uploadService.isPendingKeyFor("kyc", PERMANENT_KEY)).isFalse();
            assertThat(uploadService.isPendingKeyFor("kyc", null)).isFalse();

            /*
             * The separator matters, as it does for the folder rule it backs: a
             * prefix that is merely a prefix of the folder is a different folder.
             */
            assertThat(uploadService.isPendingKeyFor("ky", PENDING_KEY)).isFalse();
        }

        /* -------------------------------------------------------------- *
         * Promotion and cleanup failures
         * -------------------------------------------------------------- */

        @Test
        void refusesToOverwriteAnObjectAlreadyAtTheDestination() {

            /*
             * A claim on a key that is already occupied. The object there is a
             * live one — some other upload reached this key first — and deciding
             * which of the two should survive is not this flow's to decide, so
             * neither is touched: the confirmation is refused and only the
             * pending object, which can never be filed, is removed.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(2048L)));
            when(objectStore.exists(PERMANENT_KEY)).thenReturn(true);

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("Nothing was overwritten");

            verify(objectStore, never()).copy(anyString(), anyString(), anyString());
            verify(objectStore, never()).delete(PERMANENT_KEY);
            verify(objectStore).delete(PENDING_KEY);
        }

        @Test
        void leavesTheUploadPendingWhenTheCopyFails() {

            /*
             * A store failure, not a refusal, and the two must not be confused:
             * the upload is intact and correct under its pending key, so it is
             * left there and the client — who has nothing to re-send and no way
             * to re-send it — can confirm again. Removing it would make sending
             * the file a second time the only remedy for a bucket that was
             * briefly unreachable.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(2048L)));

            doThrow(new StorageException("bucket unreachable"))
                    .when(objectStore).copy(PENDING_KEY, PERMANENT_KEY, PDF);

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("confirm it again");

            verify(objectStore, never()).delete(anyString());
        }

        @Test
        void reportsAPromotionThatCannotBeReadBack() {

            /*
             * The copy reported success and the destination is empty. Rare
             * enough to be a store fault, and not something to paper over by
             * describing the object from the metadata read at the pending key:
             * the caller is about to write a row naming a key nothing is stored
             * under. Left pending, so a repeat can finish it.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(2048L)));
            when(objectStore.stat(PERMANENT_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> uploadService.confirmUpload(PENDING_KEY, PDF))
                    .isInstanceOf(StorageException.class);

            verify(objectStore, never()).delete(anyString());
        }

        @Test
        void doesNotFailAConfirmationBecauseThePendingObjectWouldNotGoAway() {

            /*
             * The object the caller is about to record is already stored and
             * checked. Failing now would tell the client their upload could not
             * be filed when it has been, and the row would not be written — so
             * the leftover is logged instead. It is unreferenced, in the
             * namespace nothing lists or serves, and removable by prefix.
             */
            when(objectStore.stat(PENDING_KEY)).thenReturn(Optional.of(pending(2048L)));
            when(objectStore.stat(PERMANENT_KEY)).thenReturn(Optional.of(promoted(2048L)));

            doThrow(new StorageException("bucket unreachable"))
                    .when(objectStore).delete(PENDING_KEY);

            assertThat(uploadService.confirmUpload(PENDING_KEY, PDF).key())
                    .isEqualTo(PERMANENT_KEY);
        }
    }
}
