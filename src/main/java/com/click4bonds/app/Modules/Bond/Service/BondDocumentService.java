package com.click4bonds.app.Modules.Bond.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondDocumentResponse;
import com.click4bonds.app.Modules.Bond.Dto.ConfirmBondUploadRequest;
import com.click4bonds.app.Modules.Bond.Dto.PresignBondUploadRequest;
import com.click4bonds.app.Modules.Bond.Dto.PresignBondUploadResponse;
import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Models.BondDocument;
import com.click4bonds.app.Modules.Bond.Models.Issuer;
import com.click4bonds.app.Modules.Bond.Repository.BondDocumentRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Storage.Model.ObjectMetadata;
import com.click4bonds.app.Modules.Storage.Service.ObjectStore;
import com.click4bonds.app.Modules.Storage.Service.UploadService;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The files attached to a bond: its logo, its information memorandum, its term
 * sheet.
 *
 * <h2>It owns no storage of its own</h2>
 *
 * <p>Nothing in this class talks to Cloudflare R2 — or knows that R2 exists.
 * Uploads go through
 * {@link com.click4bonds.app.Modules.Storage.Service.UploadService}, which
 * decides what may be uploaded, derives the key and signs the URL; reads and
 * deletions go through
 * {@link com.click4bonds.app.Modules.Storage.Service.ObjectStore}. This class
 * is the bond-shaped half: which bond a file belongs to, what it is called,
 * and what happens to a bond's logo when a new one arrives.</p>
 *
 * <h2>The three-step upload, and why it is three steps</h2>
 *
 * <ol>
 *   <li>{@link #presign} — the admin says what they are sending. The type is
 *       checked, a key is derived for this bond, and a short-lived URL is
 *       returned.</li>
 *   <li>The admin {@code PUT}s the file to the object store. A 20 MB memorandum
 *       does not pass through this application.</li>
 *   <li>{@link #confirm} — the admin reports the upload finished. The object is
 *       read back, its size and type checked, filed under the key it will live
 *       under, and only then is a document row written.</li>
 * </ol>
 *
 * <p>Step three is not a formality. It is the only point at which the size
 * limit can be applied — a signed upload cannot be given one — and the only
 * point at which this application learns that a file it authorised actually
 * arrived. A row written at step one would mean a document that exists in the
 * catalogue and not in the bucket.</p>
 *
 * <h2>The logo is a document, with one difference</h2>
 *
 * <p>A logo is uploaded, checked and stored exactly like any other file, under
 * {@link BondDocumentType#LOGO}. What differs is that an <em>issuer</em> has one
 * rather than many: confirming a logo replaces the previous one, object and row
 * together, and mirrors the new key onto
 * {@link com.click4bonds.app.Modules.Bond.Models.Issuer#getLogoKey()} so the
 * catalogue can show the same image for every bond of that issuer. This class is
 * the only writer of that field, which is what keeps the mirror honest.</p>
 *
 * <p>A {@code LOGO} is confirmed through a bond, because that is where an admin
 * already is, but it is stored against the bond's issuer — the logo is the
 * issuer's, not the bond's. A bond with no issuer therefore cannot take one.</p>
 *
 * <p>It is also the only file served publicly, from
 * {@code /api/bonds/{isin}/logo}, because it appears on a public catalogue page
 * and a browser fetching it from an {@code <img>} cannot attach a token.
 * Documents are served only to signed-in callers.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BondDocumentService {

    /** Where a bond's documents are filed, before the date and random parts. */
    private static final String DOCUMENTS = "documents";

    /** Where a bond's logo is filed. A separate folder, so it lists apart. */
    private static final String LOGO = "logo";

    private final BondDocumentRepository bondDocumentRepository;
    private final IssuerService issuerService;
    private final BondService bondService;
    private final UserService userService;
    private final UploadService uploadService;
    private final ObjectStore objectStore;

    /**
     * A document ready to be handed to a caller as a download.
     *
     * @param fileName    name to offer the browser
     * @param contentType MIME type to serve it as
     * @param content     the bytes
     */
    public record DownloadedBondDocument(String fileName, String contentType, byte[] content) {
    }

    // =========================================================
    // PRESIGN
    // =========================================================

    /**
     * Authorises one upload against a bond and returns the URL for it.
     *
     * <p>The key is derived from the bond's own stored ISIN rather than the one
     * in the path. The two are the same value after lookup, and taking it from
     * the entity means the key is built from a string this application wrote
     * rather than from request input.</p>
     *
     * @throws ResourceNotFoundException when no bond carries that ISIN
     * @throws com.click4bonds.app.Modules.Storage.Exception.UploadRejectedException
     *         when the content type is not one clients may upload, or is outside
     *         the size the policy describes
     */
    public PresignBondUploadResponse presign(
            String isin,
            PresignBondUploadRequest request) {

        Bond bond = bondService.findBond(isin);

        String prefix = prefixFor(bond, request.getDocumentType());

        UploadService.PresignedUpload upload =
                uploadService.presignUpload(prefix, request.getContentType());

        log.info(
                "Bond upload authorised: isin={} documentType={} key={}",
                bond.getIsin(),
                request.getDocumentType(),
                upload.key());

        return new PresignBondUploadResponse(
                upload.key(),
                upload.url().toString(),
                upload.contentType(),
                upload.expiresAt(),
                upload.maxSize());
    }

    // =========================================================
    // CONFIRM
    // =========================================================

    /**
     * Records an upload that has finished, after checking what arrived.
     *
     * <p>Four things are checked before a row is written. The key must be one
     * this application could have issued for <em>this</em> bond and this kind of
     * file — otherwise an admin of one bond could confirm a key belonging to
     * another, or to nothing at all. The object must exist, since a client that
     * reports success without uploading is an ordinary outcome rather than an
     * attack. Its content type and size are read from the object store rather
     * than taken on trust, because the client says what it sent and only the
     * store knows what it kept.</p>
     *
     * <p>If any check fails the object is removed, so a refused upload leaves
     * nothing behind in the bucket. What is removed is only ever the pending
     * object the client has just sent — an upload is filed under a key of its
     * own until it is confirmed, and confirmation is what moves it — so no
     * refusal here can reach a file that is already recorded against a bond. See
     * {@code UploadService}.</p>
     *
     * <p>The key recorded on the row is the one the object ends up at, not the
     * one it was sent to, and the two differ by the pending part. Everything
     * that reads a document afterwards — the download, the bond's logo mirror —
     * uses the permanent key.</p>
     *
     * <p>A {@link BondDocumentType#LOGO} replaces the bond's previous logo —
     * its object and its row — and becomes the key the catalogue reads. Every
     * other type is appended, so a bond may hold two rating reports from
     * different agencies.</p>
     *
     * @param adminId the staff member filing it, recorded on the row
     * @throws ResourceNotFoundException when no bond carries that ISIN
     * @throws BadRequestException when the key was not issued for this bond
     */
    public BondDocumentResponse confirm(
            String isin,
            ConfirmBondUploadRequest request,
            UUID adminId) {

        Bond bond = bondService.findBond(isin);

        String prefix = prefixFor(bond, request.getDocumentType());

        requireKeyIssuedFor(prefix, request.getStorageKey());

        /*
         * The check that makes the upload real: reads the object's own metadata
         * back — size and content type — and removes it if it is not what the
         * URL authorised. See UploadService for why the size cannot be bounded
         * any earlier.
         *
         * It also files the object, which is why what comes back is used below
         * rather than what was asked with: the key in the request is where the
         * file waited to be checked, and {@code metadata.key()} is where it now
         * lives. Storing the former would leave the row pointing at an object
         * the confirmation has just removed.
         */
        ObjectMetadata metadata =
                uploadService.confirmUpload(request.getStorageKey(), request.getContentType());

        String storageKey = metadata.key();

        /*
         * The logo belongs to the issuer, so it is resolved before anything is
         * written: a bond with no issuer has nowhere to put one, and failing
         * here is better than recording a document nothing points at.
         */
        Issuer issuer = request.getDocumentType() == BondDocumentType.LOGO
                ? requireIssuer(bond)
                : null;

        if (issuer != null) {
            replaceExistingLogo(bond, issuer, storageKey);
        }

        User admin = userService.getUser(adminId);

        BondDocument document = BondDocument.builder()
                .bond(bond)
                .documentType(request.getDocumentType())
                .fileName(request.getFileName().trim())
                .storageKey(storageKey)
                .contentType(metadata.contentType())
                .sizeBytes(metadata.size())
                .uploadedBy(admin)
                .build();

        BondDocument saved = bondDocumentRepository.save(document);

        if (issuer != null) {

            /*
             * The mirror, written only here. The issuer is already managed by
             * this transaction, but it is saved explicitly so the invariant
             * does not depend on how this method was entered — a caller that
             * reached it without a transaction would otherwise find the field
             * silently unsaved.
             */
            issuer.setLogoKey(saved.getStorageKey());

            issuerService.saveIssuer(issuer);

            /*
             * Every bond of this issuer embeds the logo URL in its cached
             * detail response, so all of them go stale together.
             */
            evictCachedBonds(issuer);
        }

        log.info(
                "Bond document recorded: isin={} documentType={} id={} size={}",
                bond.getIsin(),
                saved.getDocumentType(),
                saved.getId(),
                saved.getSizeBytes());

        return toResponse(bond.getIsin(), saved);
    }

    // =========================================================
    // READ
    // =========================================================

    /**
     * A bond's documents, newest first.
     *
     * <p>The logo is not among them. It is stored as a document so that it is
     * uploaded, checked and removed the same way, but it is not one of the
     * files a reader is looking for here, and it is already served from the
     * bond's own logo URL.</p>
     */
    @Transactional(readOnly = true)
    public List<BondDocumentResponse> list(String isin) {

        Bond bond = bondService.findBond(isin);

        return bondDocumentRepository
                .findByBond_IdAndDocumentTypeNotOrderByCreatedAtDesc(
                        bond.getId(),
                        BondDocumentType.LOGO)
                .stream()
                .map(document -> toResponse(bond.getIsin(), document))
                .toList();
    }

    /**
     * A signed URL for a bond's logo, for the public redirect to answer with.
     *
     * <p>A redirect rather than the bytes, unlike {@link #download}: this serves
     * an image to a browser that is rendering a catalogue page, so the fewer
     * bytes and the fewer application threads involved the better, and the file
     * is the same for every viewer.</p>
     *
     * @throws ResourceNotFoundException when the bond does not exist, or has no
     *                                   logo. The two are reported identically
     *                                   so the endpoint cannot be used to find
     *                                   out whether a bond exists
     */
    @Transactional(readOnly = true)
    public URI logoLocation(String isin) {

        Bond bond = bondService.findBond(isin);

        /*
         * The logo is the issuer's, so a bond with no issuer has none either.
         * Both cases answer the same 404, which is deliberate — see below.
         */
        String key = bond.getIssuer() == null
                ? null
                : bond.getIssuer().getLogoKey();

        if (key == null) {

            log.info("Bond {} has no logo", bond.getIsin());

            throw new ResourceNotFoundException("No logo for bond: " + isin);
        }

        return uploadService.presignDownload(key).url();
    }

    /**
     * Reads a document back, ready to be sent to a caller.
     *
     * <p>The bytes rather than a redirect, unlike {@link #logoLocation}. The
     * caller here is saving a named file, and a redirect would hand it whatever
     * the object store calls the object — a UUID — instead of the name the
     * admin gave it. It would also publish the bucket's address to every caller
     * that follows the redirect.</p>
     *
     * @throws ResourceNotFoundException when the bond does not exist, or has no
     *                                   such document
     */
    @Transactional(readOnly = true)
    public DownloadedBondDocument download(String isin, UUID documentId) {

        Bond bond = bondService.findBond(isin);

        BondDocument document = bondDocumentRepository
                .findByIdAndBond_Id(documentId, bond.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No document " + documentId + " on bond: " + isin));

        byte[] content = objectStore.get(document.getStorageKey());

        return new DownloadedBondDocument(
                document.getFileName(),
                document.getContentType(),
                content);
    }

    // =========================================================
    // DELETE
    // =========================================================

    /**
     * Removes a document: the object first, then the row.
     *
     * <p>That order matters. Deleting the row first would leave an object with
     * nothing pointing at it if the object store then failed — unreachable,
     * unpaid for, and impossible to find again. Deleting the object first can
     * only fail in the direction that leaves a row describing a file that is
     * still there, which a retry fixes.</p>
     *
     * <p>Clearing the bond's logo follows from deleting one, so a deleted logo
     * does not leave the catalogue pointing at an object that is gone.</p>
     *
     * @throws ResourceNotFoundException when the bond does not exist, or has no
     *                                   such document
     */
    public void delete(String isin, UUID documentId) {

        Bond bond = bondService.findBond(isin);

        BondDocument document = bondDocumentRepository
                .findByIdAndBond_Id(documentId, bond.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No document " + documentId + " on bond: " + isin));

        objectStore.delete(document.getStorageKey());

        bondDocumentRepository.delete(document);

        if (document.getDocumentType() == BondDocumentType.LOGO) {

            Issuer issuer = bond.getIssuer();

            /*
             * Only clear the issuer's mirror when it still points at the
             * document being removed. A newer logo may have been confirmed
             * through another bond of the same issuer, and deleting the older
             * row must not blank the current image.
             */
            if (issuer != null
                    && document.getStorageKey().equals(issuer.getLogoKey())) {

                issuer.setLogoKey(null);

                issuerService.saveIssuer(issuer);

                evictCachedBonds(issuer);
            }
        }

        log.info(
                "Bond document deleted: isin={} documentType={} id={}",
                bond.getIsin(),
                document.getDocumentType(),
                documentId);
    }

    // =========================================================
    // INTERNALS
    // =========================================================

    /**
     * Where a file of this type belongs for this bond, e.g.
     * {@code bonds/ine123a01011/documents}.
     *
     * <p>Built from the bond's stored ISIN and lower-cased: ISINs are
     * case-insensitive, and folding the case means an ISIN written one way in
     * one request and another way in the next still files under one folder. A
     * key is not a case-insensitive thing, so this has to be decided rather
     * than left to whichever spelling arrived.</p>
     */
    private static String prefixFor(Bond bond, BondDocumentType documentType) {

        String folder = documentType == BondDocumentType.LOGO ? LOGO : DOCUMENTS;

        return "bonds/" + bond.getIsin().toLowerCase(Locale.ROOT) + "/" + folder;
    }

    /**
     * Refuses a key this application would not have issued for this bond.
     *
     * <p>The reason this is not merely a sanity check: the key is the one part
     * of the confirmation a caller supplies that decides <em>which object is
     * claimed</em>. Without this, a staff member who can manage one bond could
     * confirm a key belonging to another — and, for a logo, make it that bond's
     * public image — or attach an object to a bond that never uploaded it.</p>
     *
     * <p>Asked of {@code UploadService} rather than composed here out of the
     * prefix, because a key it would accept is a narrower thing than a key
     * beginning with the bond's folder: it has to be an upload that is still
     * waiting to be confirmed, which is what stops this check drifting from the
     * one the confirmation itself makes. The bond half of the rule — that the
     * folder is <em>this</em> bond's — is the part this class owns.</p>
     *
     * @throws BadRequestException rather than a storage type, because the
     *         mismatch is this module's rule about its own bonds rather than
     *         anything the store objected to
     */
    private void requireKeyIssuedFor(String prefix, String storageKey) {

        if (uploadService.isPendingKeyFor(prefix, storageKey)) {
            return;
        }

        log.warn(
                "Rejected a confirmation for a key outside the bond's folder: expected={} got={}",
                prefix,
                storageKey);

        throw new BadRequestException(
                "That upload does not belong to this bond. Ask for a new upload URL and send the file again.");
    }

    /**
     * Takes the issuer's existing logo out of the way before a new one is
     * recorded.
     *
     * <p>The object is deleted before the row, for the reason given on
     * {@link #delete}. The old key is left on the issuer until the new document
     * is saved — {@link #confirm} sets it immediately afterwards — so a failure
     * between the two leaves the issuer pointing at a logo that still exists,
     * rather than at nothing.</p>
     *
     * <p>Two records can name the old object and both are cleared. This bond's
     * previous {@code LOGO} row is removed, and the key on the issuer itself is
     * deleted when it names a different object — which is the case when the
     * logo was last confirmed through another bond of the same issuer. The
     * replacement key is never deleted, so confirming the same key twice is
     * harmless.</p>
     */
    private void replaceExistingLogo(Bond bond, Issuer issuer, String replacementKey) {

        String previousKey = bondDocumentRepository
                .findFirstByBond_IdAndDocumentType(bond.getId(), BondDocumentType.LOGO)
                .filter(previous -> !previous.getStorageKey().equals(replacementKey))
                .map(previous -> {

                    objectStore.delete(previous.getStorageKey());

                    bondDocumentRepository.delete(previous);

                    log.info(
                            "Replaced the logo for bond {}: previousKey={}",
                            bond.getIsin(),
                            previous.getStorageKey());

                    return previous.getStorageKey();

                })
                .orElse(null);

        String issuerKey = issuer.getLogoKey();

        if (issuerKey != null
                && !issuerKey.equals(replacementKey)
                && !issuerKey.equals(previousKey)) {

            objectStore.delete(issuerKey);

            /*
             * The row that named it may belong to another bond of this issuer.
             * It describes an object that no longer exists, so it goes too.
             */
            bondDocumentRepository.deleteByStorageKey(issuerKey);

            log.info(
                    "Replaced the logo for issuer {}: previousKey={}",
                    issuer.getId(),
                    issuerKey);
        }
    }

    /**
     * The issuer a logo would belong to.
     *
     * @throws BadRequestException when the bond has no issuer, because the logo
     *         is stored on the issuer and there is nothing to attach it to
     */
    private static Issuer requireIssuer(Bond bond) {

        if (bond.getIssuer() == null) {

            throw new BadRequestException(
                    "Bond has no issuer, so it cannot have a logo: "
                            + bond.getIsin()
                            + ". Add an issuer first.");
        }

        return bond.getIssuer();
    }

    /**
     * Drops every cached bond read belonging to {@code issuer}.
     *
     * <p>The logo URL is part of the cached detail response, and it belongs to
     * the issuer rather than the bond, so changing it invalidates every bond the
     * issuer has — not only the one the upload was confirmed through.</p>
     */
    private void evictCachedBonds(Issuer issuer) {

        bondService.findIsinsByIssuerId(issuer.getId())
                .forEach(bondService::evictCachedBond);
    }

    /**
     * The download URL for one document.
     *
     * <p>Built here rather than left to the caller because it is the only part
     * of this response a client has to act on, and a client assembling it from
     * three identifiers is a client that can get it wrong. It is deliberately a
     * URL of this application rather than of the object store: the object store
     * is private, and the address of a bucket is not something to publish.</p>
     */
    private static String downloadUrl(String isin, UUID documentId) {

        return "/api/bonds/" + isin + "/documents/" + documentId + "/download";
    }

    private static BondDocumentResponse toResponse(String isin, BondDocument document) {

        return new BondDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getCreatedAt(),
                downloadUrl(isin, document.getId()));
    }
}
