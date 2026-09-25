package com.click4bonds.app.Modules.Document.Model;

/**
 * A file format the document module can produce.
 *
 * <p>Carries the extension and MIME type together so a caller never has to pair
 * them up by hand — a mismatched pair produces a download that browsers refuse
 * or, worse, open in the wrong application.</p>
 */
public enum DocumentFormat {

    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),

    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    DocumentFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    /** @return the file extension, without the leading dot */
    public String extension() {
        return extension;
    }

    /** @return the MIME type to send when serving a document of this format */
    public String contentType() {
        return contentType;
    }
}
