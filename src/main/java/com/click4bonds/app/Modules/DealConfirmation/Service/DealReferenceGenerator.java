package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.LocalDate;

/**
 * Issues the customer-facing deal reference.
 *
 * <p>An interface rather than a plain class so tests can substitute a
 * predictable generator; the production implementation is
 * {@link DealReferenceGeneratorImpl}.</p>
 */
public interface DealReferenceGenerator {

    /**
     * Allocates the next reference for the given day.
     *
     * <p>Must be called inside a transaction — the implementation takes a lock
     * on the underlying counter and relies on the transaction to release it.</p>
     *
     * @param sequenceDate day the reference belongs to (normally "today")
     * @return a reference that no other deal holds, e.g. {@code DC-20260922-000001}
     */
    String next(LocalDate sequenceDate);
}
