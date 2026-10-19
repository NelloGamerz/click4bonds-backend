package com.click4bonds.app.Modules.Bond.Enums;

/**
 * What a stored file about a bond is.
 *
 * <p>Stored as a name rather than an ordinal so a row keeps its meaning when
 * this enum gains a constant or is reordered, and so a value read out of the
 * database can be understood without consulting this file.</p>
 *
 * <p>{@link #LOGO} is the odd one out and is worth pointing at. It is a
 * document like any other — same upload, same table, same deletion — but an
 * <em>issuer</em> has at most one of them. It is mirrored onto
 * {@link com.click4bonds.app.Modules.Bond.Models.Issuer#getLogoKey()} so every
 * bond of that issuer shows the same image without a query per row, and it is
 * served from the public {@code /api/bonds/{isin}/logo} rather than from the
 * document endpoints. It is therefore excluded from the document listing: an
 * admin looking at a bond's documents does not mean "and its logo".</p>
 */
public enum BondDocumentType {

    /**
     * The logo of the bond's issuer. At most one per issuer, and shown to the
     * public on every bond of that issuer.
     */
    LOGO,

    /** The information memorandum: the offer document for the issue. */
    INFORMATION_MEMORANDUM,

    /** The term sheet: the headline terms of the issue. */
    TERM_SHEET,

    /** A credit rating agency's report on the issuer or the issue. */
    RATING_REPORT,

    /** Financial statements, audited or otherwise. */
    FINANCIAL_STATEMENT,

    /**
     * Anything else an admin needs to attach.
     *
     * <p>Exists so that filing something under the wrong heading is not the
     * only way to store a document that does not fit the four above — a
     * category that lies is worse than one that admits it is general.</p>
     */
    OTHER
}
