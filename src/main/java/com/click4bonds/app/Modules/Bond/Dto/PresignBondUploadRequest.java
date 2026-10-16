package com.click4bonds.app.Modules.Bond.Dto;

import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Asks for a URL to upload one bond file to.
 *
 * <p>Only two things are needed to sign one: what the file is, which decides
 * where it is filed, and what type it is, which the object store enforces. The
 * file's name and its size are not asked for here — the name is not trusted to
 * build a key from, and the size cannot be bound by a signed upload — so both
 * are supplied with the confirmation that follows, and the size is then read
 * back rather than believed.</p>
 */
@Data
public class PresignBondUploadRequest {

    /**
     * What the file is. Decides the key it is filed under, and — for
     * {@link BondDocumentType#LOGO} — that it replaces the bond's existing logo
     * rather than joining its documents.
     */
    @NotNull
    private BondDocumentType documentType;

    /**
     * MIME type of the file, e.g. {@code application/pdf}.
     *
     * <p>Anything after a {@code ;} is ignored, so a browser's
     * {@code application/pdf; charset=binary} is judged as {@code application/pdf}.
     * A type outside {@code storage.r2.allowed-content-types} is refused here,
     * before a URL exists for it.</p>
     */
    @NotBlank
    private String contentType;
}
