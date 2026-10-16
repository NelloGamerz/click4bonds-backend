package com.click4bonds.app.Modules.Bond.Controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondDocumentResponse;
import com.click4bonds.app.Modules.Bond.Service.BondDocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Reads of the files attached to a bond.
 *
 * <p>Sits beside {@link BondController} under {@code /api/bonds} because these
 * are part of reading a bond, not of managing one — the writes are all in
 * {@link AdminBondDocumentController}.</p>
 *
 * <h2>Why the logo is public and the documents are not</h2>
 *
 * <p>The logo is served from a path the filter chain leaves open, and the
 * documents from one that does not. That difference is deliberate.</p>
 *
 * <p>A logo appears on the public catalogue card, which anybody may browse, and
 * it is fetched by an {@code <img>} element — which cannot carry an
 * {@code Authorization} header, so an authenticated logo endpoint would simply
 * not work for its only caller. It is also answered as a redirect to a
 * short-lived signed URL rather than by streaming the image: the same file is
 * served to every viewer, and there is no reason for those bytes to pass
 * through this application.</p>
 *
 * <p>An information memorandum is a different matter. It is a document an admin
 * chose to attach, its contents are not reviewed by anything here, and the safe
 * default for something uploaded rather than generated is that it is not
 * readable by the public until someone decides otherwise. Requiring a signed-in
 * caller costs a login and can be relaxed by an explicit rule in
 * {@code SecurityConfig}; the opposite mistake cannot be corrected after the
 * fact.</p>
 */
@RestController
@RequestMapping("/api/bonds/{isin}")
@RequiredArgsConstructor
public class BondDocumentController {

    private final BondDocumentService bondDocumentService;

    /**
     * The bond's logo.
     *
     * <p>Answers a redirect to a signed URL that expires within minutes, so the
     * bytes are fetched from the object store by the browser rather than
     * relayed here, and the bucket stays private. A caller should treat this as
     * the stable address — the URL it redirects to is not.</p>
     *
     * <p>Public, because the catalogue card is public and an image element
     * cannot authenticate. See the class javadoc.</p>
     *
     * @param isin the bond whose logo is wanted
     * @return 302 to the signed URL, or 404 when the bond has no logo. A bond
     *         that does not exist answers identically, so this cannot be used
     *         to test which ISINs are real
     */
    @GetMapping("/logo")
    public ResponseEntity<Void> logo(@PathVariable String isin) {

        URI location = bondDocumentService.logoLocation(isin);

        return ResponseEntity
                .status(302)
                .location(location)
                .build();
    }

    /**
     * The bond's documents, newest first, without the logo.
     *
     * <p>Requires a signed-in caller. See the class javadoc for why this
     * differs from {@link #logo}.</p>
     *
     * @param isin the bond to list
     */
    @GetMapping("/documents")
    public ResponseEntity<List<BondDocumentResponse>> listDocuments(
            @PathVariable String isin) {

        return ResponseEntity.ok(
                bondDocumentService.list(isin));
    }

    /**
     * Downloads one document.
     *
     * <p>The bytes are streamed rather than redirected, unlike {@link #logo}.
     * The caller here is saving a named file, and a redirect would offer it
     * whatever the object store calls the object — a UUID — instead of the name
     * an admin gave it. It would also publish the bucket's address to anyone who
     * followed it.</p>
     *
     * @param isin       the bond it belongs to
     * @param documentId the document to fetch
     * @return the file as an attachment, named as it was recorded
     */
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String isin,
            @PathVariable UUID documentId) {

        BondDocumentService.DownloadedBondDocument document =
                bondDocumentService.download(isin, documentId);

        /*
         * Sent as an attachment so the browser saves the memorandum rather than
         * trying to render a PDF in a tab, and named after the file the admin
         * recorded so two downloads do not arrive as the same thing.
         */
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(document.content());
    }
}
