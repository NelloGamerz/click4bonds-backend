package com.click4bonds.app.Modules.Bond.Dto;

import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Reports that an upload has finished, so it can be checked and recorded.
 *
 * <p>The step that turns a signed URL into a document. The {@code storageKey}
 * and {@code contentType} come back from
 * {@link PresignBondUploadResponse} unchanged; the key is not trusted on its
 * own — it is checked to be an unconfirmed upload this application issued for
 * this bond and this kind of file — and the content type is compared against
 * what the object store actually stored rather than assumed.</p>
 *
 * <p>This call is also what applies the size limit, because a signed upload
 * cannot be given one. A file over
 * {@code storage.r2.max-upload-size} is removed and the request fails, so a
 * refused upload leaves nothing behind.</p>
 */
@Data
public class ConfirmBondUploadRequest {

    /**
     * The key returned by the presign, reported back unchanged.
     *
     * <p>Rejected unless it is one this application could have issued for this
     * bond, so a caller cannot use this endpoint to claim an object belonging
     * to somebody else's bond.</p>
     */
    @NotBlank
    @Size(max = 512)
    private String storageKey;

    /** What the file is. Must be the type the URL was signed for. */
    @NotNull
    private BondDocumentType documentType;

    /**
     * The name to offer a person downloading it.
     *
     * <p>Record-keeping only: it is stored on the row and sent as the download
     * filename, and it never reaches the object key, which is derived from the
     * bond and a random identifier.</p>
     */
    @NotBlank
    @Size(max = 255)
    private String fileName;

    /** The type the upload was authorised as, exactly as the presign returned it. */
    @NotBlank
    @Size(max = 150)
    private String contentType;
}
