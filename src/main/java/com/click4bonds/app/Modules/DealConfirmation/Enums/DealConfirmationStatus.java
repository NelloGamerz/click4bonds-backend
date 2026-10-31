package com.click4bonds.app.Modules.DealConfirmation.Enums;

/**
 * Lifecycle of a deal confirmation.
 *
 * <pre>
 * CREATED --confirm--&gt; CONFIRMED --document produced--&gt; CONFIRMATION_GENERATED
 *    |
 *    +--cancel--&gt; CANCELLED
 * </pre>
 *
 * <p>A back-office deal is {@link #CREATED} when its inventory has been
 * reserved; it stays editable until an employee with the {@code
 * DEAL_CONFIRMATION} grant confirms it. {@link #CONFIRMED} is terminal for the
 * deal's figures — nothing on a confirmed deal may change — and it is what
 * releases the document step. {@link #CANCELLED} returns the reserved units to
 * the bond. A customer-placed deal skips the confirmation step: it is
 * lettered immediately, as it always was.</p>
 *
 * <p>A deal whose document generation fails stays {@link #CONFIRMED} with no
 * document, so the failure is visible as a confirmed deal awaiting its letter
 * rather than as a deal that never happened.</p>
 */
public enum DealConfirmationStatus {

    /** Deal persisted and inventory reserved; document not generated yet. */
    CREATED,

    /**
     * Confirmed by an employee with {@code DEAL_CONFIRMATION}; frozen and
     * awaiting its confirmation letter.
     */
    CONFIRMED,

    /** Confirmation document generated and stored against the deal. */
    CONFIRMATION_GENERATED,

    /** Confirmed never; the reserved units have been returned to the bond. */
    CANCELLED
}
