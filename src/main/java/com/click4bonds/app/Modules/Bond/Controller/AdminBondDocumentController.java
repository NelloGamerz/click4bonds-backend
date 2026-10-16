package com.click4bonds.app.Modules.Bond.Controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondDocumentResponse;
import com.click4bonds.app.Modules.Bond.Dto.ConfirmBondUploadRequest;
import com.click4bonds.app.Modules.Bond.Dto.PresignBondUploadRequest;
import com.click4bonds.app.Modules.Bond.Dto.PresignBondUploadResponse;
import com.click4bonds.app.Modules.Bond.Service.BondDocumentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin management of the files attached to a bond.
 *
 * <p>Guarded by the same rule as {@link AdminBondController} — a staff role plus
 * the {@code BOND_MANAGEMENT} grant — because attaching an information
 * memorandum to a bond is editing that bond, and an employee trusted to change
 * its price is the one trusted to change what it publishes.</p>
 *
 * <p>Uploading is two calls, not one, and that is the shape of a direct-to-
 * storage upload rather than an oversight:</p>
 *
 * <ol>
 *   <li>{@code POST .../documents/presign} — say what you are sending, receive a
 *       short-lived URL and the key the file will be stored under.</li>
 *   <li>{@code PUT} the file to that URL. It goes to the object store, not
 *       through this application, so a large memorandum does not occupy a
 *       request thread or a heap buffer here.</li>
 *   <li>{@code POST .../documents} — report the key back, and the file is
 *       checked and recorded.</li>
 * </ol>
 *
 * <p>Nothing is listed or downloadable until step three. A file that was
 * authorised but never confirmed exists in the bucket and nowhere else, which is
 * why an abandoned upload leaves no trace in the catalogue.</p>
 */
@RestController
@RequestMapping("/api/admin/bonds/{isin}/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'BOND_MANAGEMENT')")
public class AdminBondDocumentController {

    private final BondDocumentService bondDocumentService;

    /**
     * Authorises one upload and returns the URL to send the file to.
     *
     * @param isin    the bond the file belongs to
     * @param request what the file is, and what type it is
     * @return 201 with the signed URL, its expiry, the size ceiling and the
     *         storage key to report back
     */
    @PostMapping("/presign")
    public ResponseEntity<PresignBondUploadResponse> presign(
            @PathVariable String isin,
            @Valid @RequestBody PresignBondUploadRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        bondDocumentService.presign(
                                isin,
                                request));
    }

    /**
     * Records an upload that has finished, after checking it.
     *
     * <p>Answers 201 on the first confirmation. Confirming the same key twice
     * fails: the storage key is unique, so the second attempt is rejected rather
     * than filing the same object under two rows.</p>
     *
     * @param isin    the bond the file belongs to
     * @param request the key, type and name from the presign
     * @param jwt     verified token; its subject identifies the staff member
     * @return 201 with the recorded document
     */
    @PostMapping
    public ResponseEntity<BondDocumentResponse> confirm(
            @PathVariable String isin,
            @Valid @RequestBody ConfirmBondUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        bondDocumentService.confirm(
                                isin,
                                request,
                                adminId));
    }

    /**
     * The bond's documents, newest first.
     *
     * <p>The logo is not among them: it is stored as a document but served from
     * the bond's own logo URL, and an admin looking at a bond's documents does
     * not mean "and its logo".</p>
     *
     * @param isin the bond to list
     */
    @GetMapping
    public ResponseEntity<List<BondDocumentResponse>> list(
            @PathVariable String isin) {

        return ResponseEntity.ok(
                bondDocumentService.list(isin));
    }

    /**
     * Removes a document and the object behind it.
     *
     * @param isin       the bond it belongs to
     * @param documentId the document to remove
     * @return 204 when it is gone
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String isin,
            @PathVariable UUID documentId) {

        bondDocumentService.delete(isin, documentId);

        return ResponseEntity.noContent().build();
    }
}
