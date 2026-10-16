package com.click4bonds.app.Modules.Bond.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Dto.BondDocumentResponse;
import com.click4bonds.app.Modules.Bond.Dto.ConfirmBondUploadRequest;
import com.click4bonds.app.Modules.Bond.Dto.PresignBondUploadRequest;
import com.click4bonds.app.Modules.Bond.Dto.PresignBondUploadResponse;
import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Models.BondDocument;
import com.click4bonds.app.Modules.Bond.Repository.BondDocumentRepository;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Storage.Model.ObjectMetadata;
import com.click4bonds.app.Modules.Storage.Service.ObjectStore;
import com.click4bonds.app.Modules.Storage.Service.UploadService;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

/**
 * How a bond's files are filed, checked and removed.
 *
 * <p>Mocked repositories and a mocked storage module: what matters here is this
 * class's own decisions — which prefix a bond's file lands under, which key may
 * be confirmed, what a logo replaces and what the bond's mirror is left
 * pointing at. Nothing in these tests touches a database or a bucket, so they
 * run anywhere; the storage module's own tests cover the signing and the size
 * check.</p>
 *
 * <p>The ownership check gets the most attention, because it is the one place a
 * caller supplies something that decides <em>which object is claimed</em>. Every
 * other value this service takes from a request is either a label or is verified
 * against the object store; the key is neither.</p>
 */
@ExtendWith(MockitoExtension.class)
class BondDocumentServiceTest {

    private static final String ISIN = "INE123A01011";
    private static final String LOWER_ISIN = "ine123a01011";
    private static final String PDF = "application/pdf";
    private static final UUID ADMIN_ID = UUID.randomUUID();

    /** Random part of a derived key, as {@code UploadService} builds one. */
    private static final String UUID_PART = "3f2a1c44-5b6e-4d7a-9c1f-0a2b3c4d5e6f";

    /** Where an upload waits, and what the presign hands to the client. */
    private static final String PENDING_DOCUMENT_KEY =
            "pending/bonds/" + LOWER_ISIN + "/documents/2026/10/" + UUID_PART + ".pdf";

    private static final String PENDING_LOGO_KEY =
            "pending/bonds/" + LOWER_ISIN + "/logo/2026/10/" + UUID_PART + ".png";

    /** Where those objects live once confirmed: what a row records. */
    private static final String DOCUMENT_KEY =
            "bonds/" + LOWER_ISIN + "/documents/2026/10/" + UUID_PART + ".pdf";

    private static final String LOGO_KEY =
            "bonds/" + LOWER_ISIN + "/logo/2026/10/" + UUID_PART + ".png";

    @Mock
    private BondDocumentRepository bondDocumentRepository;

    @Mock
    private BondRepository bondRepository;

    @Mock
    private BondService bondService;

    @Mock
    private UserService userService;

    @Mock
    private UploadService uploadService;

    @Mock
    private ObjectStore objectStore;

    private BondDocumentService bondDocumentService;

    private Bond bond;

    @BeforeEach
    void setUp() {

        bondDocumentService = new BondDocumentService(
                bondDocumentRepository,
                bondRepository,
                bondService,
                userService,
                uploadService,
                objectStore);

        bond = Bond.builder()
                .id(UUID.randomUUID())
                .isin(ISIN)
                .name("A bond")
                .build();

        /*
         * Lenient: nearly every test resolves the bond through the service, but
         * the two that assert the not-found path deliberately do not.
         */
        lenient().when(bondService.findBond(ISIN)).thenReturn(bond);
    }

    /* ------------------------------------------------------------------ *
     * Presign
     * ------------------------------------------------------------------ */

    @Test
    void filesUnderAPrefixBuiltFromTheStoredIsin() {

        when(uploadService.presignUpload(anyString(), anyString())).thenReturn(
                new UploadService.PresignedUpload(
                        PENDING_DOCUMENT_KEY, URI.create("https://signed"), PDF, Instant.now(), 1024L));

        PresignBondUploadRequest request = new PresignBondUploadRequest();
        request.setDocumentType(BondDocumentType.INFORMATION_MEMORANDUM);
        request.setContentType(PDF);

        PresignBondUploadResponse response = bondDocumentService.presign(ISIN, request);

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);

        verify(uploadService).presignUpload(prefix.capture(), eq(PDF));

        /*
         * Lower-cased, and built from the bond's own stored ISIN rather than
         * from the path variable. ISINs are case-insensitive, so folding the
         * case means the same bond files under one folder however the ISIN was
         * spelled in the request.
         */
        assertThat(prefix.getValue()).isEqualTo("bonds/" + LOWER_ISIN + "/documents");

        assertThat(response.storageKey()).isEqualTo(PENDING_DOCUMENT_KEY);
        assertThat(response.contentType()).isEqualTo(PDF);
    }

    @Test
    void filesALogoInItsOwnFolder() {

        when(uploadService.presignUpload(anyString(), anyString())).thenReturn(
                new UploadService.PresignedUpload(
                        PENDING_LOGO_KEY, URI.create("https://signed"), "image/png", Instant.now(), 1024L));

        PresignBondUploadRequest request = new PresignBondUploadRequest();
        request.setDocumentType(BondDocumentType.LOGO);
        request.setContentType("image/png");

        bondDocumentService.presign(ISIN, request);

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);

        verify(uploadService).presignUpload(prefix.capture(), anyString());

        /*
         * A separate folder, so a bond's logo lists apart from its documents and
         * a prefix cannot be shared between the two kinds of file.
         */
        assertThat(prefix.getValue()).isEqualTo("bonds/" + LOWER_ISIN + "/logo");
    }

    @Test
    void refusesToPresignForABondThatDoesNotExist() {

        when(bondService.findBond("NOSUCHISIN"))
                .thenThrow(new ResourceNotFoundException("Bond not found"));

        PresignBondUploadRequest request = new PresignBondUploadRequest();
        request.setDocumentType(BondDocumentType.TERM_SHEET);
        request.setContentType(PDF);

        assertThatThrownBy(() -> bondDocumentService.presign("NOSUCHISIN", request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(uploadService);
    }

    /* ------------------------------------------------------------------ *
     * Confirmation: the ownership check
     * ------------------------------------------------------------------ */

    @Test
    void refusesAKeyThatBelongsToAnotherBond() {

        /*
         * The one place a caller supplies something that decides which object is
         * claimed. Without this check, a staff member who can manage one bond
         * could confirm a key belonging to another — and, for a logo, make it
         * that bond's public image.
         */
        ConfirmBondUploadRequest request = confirmRequest(
                "pending/bonds/otherisin/documents/2026/10/" + UUID_PART + ".pdf",
                BondDocumentType.INFORMATION_MEMORANDUM);

        assertThatThrownBy(() -> bondDocumentService.confirm(ISIN, request, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to this bond");

        /*
         * Nothing is confirmed, so nothing is read from the store and no object
         * is promoted: the key is rejected before any object is looked at, so an
         * unknown key cannot be used to probe which objects exist. Asked of the
         * upload module rather than composed here, which is why the check itself
         * is a call — and every other call to it is what this rules out.
         */
        verify(uploadService, never()).confirmUpload(anyString(), anyString());
    }

    @Test
    void refusesAKeyThisBondAlreadyHas() {

        /*
         * A key that has been confirmed before, reported back a second time.
         * It is this bond's own folder and it names a real object — which is
         * exactly why it must not be accepted: the object at it is live and
         * recorded, and a confirmation is also what files an upload. What makes
         * a key claimable is that it is still waiting, not merely that it is in
         * the right folder.
         */
        ConfirmBondUploadRequest request = confirmRequest(
                DOCUMENT_KEY, BondDocumentType.INFORMATION_MEMORANDUM);

        assertThatThrownBy(() -> bondDocumentService.confirm(ISIN, request, ADMIN_ID))
                .isInstanceOf(BadRequestException.class);

        verify(uploadService, never()).confirmUpload(anyString(), anyString());
    }

    @Test
    void refusesADocumentKeyWhenALogoWasExpected() {

        /*
         * The folder and the type have to agree. Confirming a document key as a
         * LOGO would otherwise let one file be both, and would set the bond's
         * mirror to a key in the wrong folder.
         */
        ConfirmBondUploadRequest request =
                confirmRequest(PENDING_DOCUMENT_KEY, BondDocumentType.LOGO);

        assertThatThrownBy(() -> bondDocumentService.confirm(ISIN, request, ADMIN_ID))
                .isInstanceOf(BadRequestException.class);

        verify(uploadService, never()).confirmUpload(anyString(), anyString());
    }

    @Test
    void refusesAKeyThatIsMerelyAPrefixOfTheFolder() {

        /*
         * The separator matters. Without it, "bonds/ine123a01011/documents-old"
         * would pass a naive startsWith check and file a document outside the
         * folder it was authorised for.
         */
        ConfirmBondUploadRequest request = confirmRequest(
                "pending/bonds/" + LOWER_ISIN + "/documents-old/2026/10/" + UUID_PART + ".pdf",
                BondDocumentType.INFORMATION_MEMORANDUM);

        assertThatThrownBy(() -> bondDocumentService.confirm(ISIN, request, ADMIN_ID))
                .isInstanceOf(BadRequestException.class);

        verify(uploadService, never()).confirmUpload(anyString(), anyString());
    }

    /* ------------------------------------------------------------------ *
     * Confirmation: the happy path
     * ------------------------------------------------------------------ */

    @Test
    void recordsADocumentAfterCheckingIt() {

        when(uploadService.isPendingKeyFor("bonds/" + LOWER_ISIN + "/documents", PENDING_DOCUMENT_KEY))
                .thenReturn(true);

        /*
         * The confirmation answers with the key the object now has, which is not
         * the one it was sent to: the upload was filed under its pending key and
         * the confirmation is what moved it.
         */
        when(uploadService.confirmUpload(PENDING_DOCUMENT_KEY, PDF)).thenReturn(
                new ObjectMetadata(DOCUMENT_KEY, PDF, 4096L, "etag", Instant.now()));

        when(userService.getUser(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());

        when(bondDocumentRepository.save(any(BondDocument.class)))
                .thenAnswer(invocation -> {

                    BondDocument document = invocation.getArgument(0);
                    document.setId(UUID.randomUUID());
                    document.setCreatedAt(Instant.now());

                    return document;
                });

        BondDocumentResponse response = bondDocumentService.confirm(
                ISIN,
                confirmRequest(PENDING_DOCUMENT_KEY, BondDocumentType.INFORMATION_MEMORANDUM),
                ADMIN_ID);

        assertThat(response.documentType()).isEqualTo(BondDocumentType.INFORMATION_MEMORANDUM);
        assertThat(response.fileName()).isEqualTo("memorandum.pdf");
        assertThat(response.sizeBytes()).isEqualTo(4096L);
        assertThat(response.downloadUrl()).isEqualTo(
                "/api/bonds/" + ISIN + "/documents/" + response.id() + "/download");

        /*
         * The size comes from the object store's reply, not from the request.
         * The client says what it sent; only the store knows what it kept.
         */
        ArgumentCaptor<BondDocument> saved = ArgumentCaptor.forClass(BondDocument.class);

        verify(bondDocumentRepository).save(saved.capture());

        assertThat(saved.getValue().getSizeBytes()).isEqualTo(4096L);
        assertThat(saved.getValue().getBond()).isEqualTo(bond);

        /*
         * And the key recorded is the one the object ended up at. Storing the
         * pending key — the one the request reported — would point the row at an
         * object the confirmation has just removed, so the download would 404
         * and the delete would find nothing.
         */
        assertThat(saved.getValue().getStorageKey()).isEqualTo(DOCUMENT_KEY);

        /* A document is not a logo, so the mirror is untouched. */
        assertThat(bond.getLogoKey()).isNull();
        verify(bondRepository, never()).save(any(Bond.class));
    }

    /* ------------------------------------------------------------------ *
     * The logo invariant
     * ------------------------------------------------------------------ */

    @Test
    void replacesThePreviousLogoAndMirrorsTheNewKey() {

        BondDocument previous = BondDocument.builder()
                .id(UUID.randomUUID())
                .bond(bond)
                .documentType(BondDocumentType.LOGO)
                .storageKey("bonds/" + LOWER_ISIN + "/logo/2025/01/old.png")
                .fileName("old.png")
                .contentType("image/png")
                .sizeBytes(10L)
                .build();

        when(uploadService.isPendingKeyFor("bonds/" + LOWER_ISIN + "/logo", PENDING_LOGO_KEY))
                .thenReturn(true);

        when(uploadService.confirmUpload(PENDING_LOGO_KEY, "image/png")).thenReturn(
                new ObjectMetadata(LOGO_KEY, "image/png", 2048L, "etag", Instant.now()));

        when(userService.getUser(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());

        when(bondDocumentRepository.findFirstByBond_IdAndDocumentType(
                bond.getId(), BondDocumentType.LOGO))
                .thenReturn(Optional.of(previous));

        when(bondDocumentRepository.save(any(BondDocument.class)))
                .thenAnswer(invocation -> {

                    BondDocument document = invocation.getArgument(0);
                    document.setId(UUID.randomUUID());
                    document.setCreatedAt(Instant.now());

                    return document;
                });

        bondDocumentService.confirm(
                ISIN,
                confirmRequest(PENDING_LOGO_KEY, BondDocumentType.LOGO, "image/png"),
                ADMIN_ID);

        /* The old object goes before its row, so nothing is left unreferenced. */
        verify(objectStore).delete("bonds/" + LOWER_ISIN + "/logo/2025/01/old.png");
        verify(bondDocumentRepository).delete(previous);

        /* And the bond now points at the new one — the promoted key, not the
         * pending one the upload was sent to. */
        assertThat(bond.getLogoKey()).isEqualTo(LOGO_KEY);
        verify(bondRepository).save(bond);
    }

    @Test
    void doesNotDeleteTheObjectAConfirmationIsAbout() {

        /*
         * The bond's existing logo is the very object being confirmed — it was
         * already filed under the key the confirmation promotes to. Taking the
         * previous logo out of the way must not take that one, or the row about
         * to be written would describe an object that has just been deleted.
         */
        BondDocument previous = BondDocument.builder()
                .id(UUID.randomUUID())
                .bond(bond)
                .documentType(BondDocumentType.LOGO)
                .storageKey(LOGO_KEY)
                .fileName("logo.png")
                .contentType("image/png")
                .sizeBytes(10L)
                .build();

        when(uploadService.isPendingKeyFor("bonds/" + LOWER_ISIN + "/logo", PENDING_LOGO_KEY))
                .thenReturn(true);

        when(uploadService.confirmUpload(PENDING_LOGO_KEY, "image/png")).thenReturn(
                new ObjectMetadata(LOGO_KEY, "image/png", 2048L, "etag", Instant.now()));

        when(userService.getUser(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());

        when(bondDocumentRepository.findFirstByBond_IdAndDocumentType(
                bond.getId(), BondDocumentType.LOGO))
                .thenReturn(Optional.of(previous));

        when(bondDocumentRepository.save(any(BondDocument.class)))
                .thenAnswer(invocation -> {

                    BondDocument document = invocation.getArgument(0);
                    document.setId(UUID.randomUUID());
                    document.setCreatedAt(Instant.now());

                    return document;
                });

        bondDocumentService.confirm(
                ISIN,
                confirmRequest(PENDING_LOGO_KEY, BondDocumentType.LOGO, "image/png"),
                ADMIN_ID);

        verify(objectStore, never()).delete(anyString());
    }

    /* ------------------------------------------------------------------ *
     * Reading
     * ------------------------------------------------------------------ */

    @Test
    void listsADocumentsDocumentsButNotItsLogo() {

        when(bondDocumentRepository.findByBond_IdAndDocumentTypeNotOrderByCreatedAtDesc(
                bond.getId(), BondDocumentType.LOGO))
                .thenReturn(List.of());

        assertThat(bondDocumentService.list(ISIN)).isEmpty();

        /*
         * The exclusion is in the query rather than applied to the result, so
         * the logo is never loaded and the intent is visible where it is
         * decided.
         */
        verify(bondDocumentRepository)
                .findByBond_IdAndDocumentTypeNotOrderByCreatedAtDesc(
                        bond.getId(), BondDocumentType.LOGO);
    }

    @Test
    void readsADocumentBackWithTheNameItWasFiledUnder() {

        UUID documentId = UUID.randomUUID();

        BondDocument document = BondDocument.builder()
                .id(documentId)
                .bond(bond)
                .documentType(BondDocumentType.INFORMATION_MEMORANDUM)
                .storageKey(DOCUMENT_KEY)
                .fileName("ATSPL-IM-2026.pdf")
                .contentType(PDF)
                .sizeBytes(4L)
                .build();

        when(bondDocumentRepository.findByIdAndBond_Id(documentId, bond.getId()))
                .thenReturn(Optional.of(document));

        when(objectStore.get(DOCUMENT_KEY)).thenReturn(new byte[] {1, 2, 3, 4});

        BondDocumentService.DownloadedBondDocument downloaded =
                bondDocumentService.download(ISIN, documentId);

        /*
         * The name the admin gave it, not the object key. The key is a UUID, and
         * a download named after it would tell the person saving it nothing.
         */
        assertThat(downloaded.fileName()).isEqualTo("ATSPL-IM-2026.pdf");
        assertThat(downloaded.contentType()).isEqualTo(PDF);
        assertThat(downloaded.content()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void cannotReadADocumentThatBelongsToAnotherBond() {

        /*
         * Scoped in the query, so a document id from another bond simply does
         * not resolve — and the caller cannot tell "not yours" from "does not
         * exist".
         */
        UUID documentId = UUID.randomUUID();

        when(bondDocumentRepository.findByIdAndBond_Id(documentId, bond.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bondDocumentService.download(ISIN, documentId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(objectStore);
    }

    @Test
    void redirectsTheLogoToASignedUrl() {

        bond.setLogoKey(LOGO_KEY);

        when(uploadService.presignDownload(LOGO_KEY)).thenReturn(
                new UploadService.PresignedDownload(
                        LOGO_KEY, URI.create("https://signed-logo"), Instant.now()));

        assertThat(bondDocumentService.logoLocation(ISIN))
                .isEqualTo(URI.create("https://signed-logo"));
    }

    @Test
    void reportsABondWithNoLogoAsNotFound() {

        /*
         * Reported as not found rather than as a null or an empty redirect: a
         * 404 is what a browser's image element needs to fall back to whatever
         * placeholder it has, and it is the same answer a bond that does not
         * exist gets — so the endpoint cannot be used to test which ISINs are
         * real.
         */
        assertThatThrownBy(() -> bondDocumentService.logoLocation(ISIN))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(uploadService, never()).presignDownload(anyString());
    }

    /* ------------------------------------------------------------------ *
     * Deletion
     * ------------------------------------------------------------------ */

    @Test
    void deletesTheObjectBeforeTheRow() {

        UUID documentId = UUID.randomUUID();

        BondDocument document = BondDocument.builder()
                .id(documentId)
                .bond(bond)
                .documentType(BondDocumentType.TERM_SHEET)
                .storageKey(DOCUMENT_KEY)
                .fileName("terms.pdf")
                .contentType(PDF)
                .sizeBytes(4L)
                .build();

        when(bondDocumentRepository.findByIdAndBond_Id(documentId, bond.getId()))
                .thenReturn(Optional.of(document));

        bondDocumentService.delete(ISIN, documentId);

        /*
         * Object first. Deleting the row first would leave an object with
         * nothing pointing at it if the store then failed — unreachable and
         * impossible to find again. In this order the only failure is a row
         * describing a file that is still there, which a retry fixes.
         */
        var order = org.mockito.Mockito.inOrder(objectStore, bondDocumentRepository);

        order.verify(objectStore).delete(DOCUMENT_KEY);
        order.verify(bondDocumentRepository).delete(document);

        /* A document is not a logo, so the mirror is untouched. */
        assertThat(bond.getLogoKey()).isNull();
        verify(bondRepository, never()).save(any(Bond.class));
    }

    @Test
    void clearsTheBondsLogoWhenTheLogoIsDeleted() {

        UUID documentId = UUID.randomUUID();

        bond.setLogoKey(LOGO_KEY);

        BondDocument document = BondDocument.builder()
                .id(documentId)
                .bond(bond)
                .documentType(BondDocumentType.LOGO)
                .storageKey(LOGO_KEY)
                .fileName("logo.png")
                .contentType("image/png")
                .sizeBytes(4L)
                .build();

        when(bondDocumentRepository.findByIdAndBond_Id(documentId, bond.getId()))
                .thenReturn(Optional.of(document));

        bondDocumentService.delete(ISIN, documentId);

        /*
         * Otherwise the catalogue would keep pointing at an object that is gone,
         * and every card would show a broken image.
         */
        assertThat(bond.getLogoKey()).isNull();
        verify(bondRepository).save(bond);
    }

    private static ConfirmBondUploadRequest confirmRequest(
            String storageKey,
            BondDocumentType documentType) {

        return confirmRequest(storageKey, documentType, PDF);
    }

    private static ConfirmBondUploadRequest confirmRequest(
            String storageKey,
            BondDocumentType documentType,
            String contentType) {

        ConfirmBondUploadRequest request = new ConfirmBondUploadRequest();

        request.setStorageKey(storageKey);
        request.setDocumentType(documentType);
        request.setFileName("memorandum.pdf");
        request.setContentType(contentType);

        return request;
    }
}
