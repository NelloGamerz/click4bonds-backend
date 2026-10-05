package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.util.UUID;

/**
 * One request to produce a deal's confirmation letter, as it travels over Kafka.
 *
 * <p>Published the moment a deal has been committed, and consumed by
 * {@code DealConfirmationDocumentConsumer}, which fills the template, renders
 * the PDF and stores both. The HTTP request does not wait for any of that: the
 * customer is answered as soon as the deal row exists.</p>
 *
 * <p><strong>It carries the whole snapshot, not a deal id to look up.</strong>
 * {@link DealConfirmationDocumentData} is already a flat, complete record
 * because the document step has always been forbidden from reading the database
 * — it runs after the deal's transaction has committed. That makes it exactly
 * what a Kafka payload wants: the consumer needs no repository, no session and
 * no lazy association, so a message that arrives hours late still renders the
 * letter the deal was actually struck on.</p>
 *
 * <p>The {@code dealId} travels beside the snapshot because the snapshot is
 * keyed by the customer-facing {@code dealReference} while recording the result
 * is a bulk UPDATE keyed on the primary key. Neither is derivable from the
 * other.</p>
 *
 * <p>Serialised as JSON by the producer's {@code JsonSerializer} and read back
 * by a deserialiser pinned to this type — see
 * {@link com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentKafkaConfig}.
 * No type header is written, so the record must stay in the package the consumer
 * factory is told to expect.</p>
 *
 * @param dealId       primary key of the deal the document belongs to, used to
 *                     record where the letter landed
 * @param documentData everything the letter prints, captured inside the deal's
 *                     transaction so it does not follow later edits to the bond
 */
public record DealConfirmationDocumentEvent(
        UUID dealId,
        DealConfirmationDocumentData documentData) {
}
