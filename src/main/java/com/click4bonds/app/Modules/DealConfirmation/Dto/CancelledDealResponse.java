package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.time.Instant;

/**
 * The deal a cancel request answers with: just enough to update the row the
 * employee was looking at.
 *
 * <p>Deliberately not {@link DealConfirmationResponse}: that mapping reads the
 * bond's name through its lazy association, which is no longer loadable once
 * the cancellation has released the units and cleared the persistence context.
 * The fields here are either copied on the deal row or fetched eagerly with it,
 * so building this response cannot trigger a lazy load.</p>
 */
public record CancelledDealResponse(
        String bondName,
        Long lotSize,
        Instant createdAt,
        String createdBy,
        String clientName) {
}
