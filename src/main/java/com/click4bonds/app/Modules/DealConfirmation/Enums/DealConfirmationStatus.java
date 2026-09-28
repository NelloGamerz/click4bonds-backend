package com.click4bonds.app.Modules.DealConfirmation.Enums;

/**
 * Lifecycle of a deal confirmation.
 *
 * <p>Deliberately only two states. There is no CRM approval and no manual
 * approval step in this flow: a deal is created the moment its inventory has
 * been reserved, so a persisted deal is always {@link #CREATED} at first.</p>
 *
 * <p>{@link #CONFIRMATION_GENERATED} is the extension point for the document
 * workflow that is not implemented yet (Excel template fill &rarr; PDF). When
 * {@code DealConfirmationDocumentService} gains a real implementation, the deal
 * moves to this status once its document has been stored. A deal whose document
 * generation fails stays {@link #CREATED} — a failed deal is never persisted at
 * all, because creation and inventory reservation share one transaction.</p>
 */
public enum DealConfirmationStatus {

    /** Deal persisted and inventory reserved; document not generated yet. */
    CREATED,

    /** Confirmation document generated and stored against the deal. */
    CONFIRMATION_GENERATED
}
