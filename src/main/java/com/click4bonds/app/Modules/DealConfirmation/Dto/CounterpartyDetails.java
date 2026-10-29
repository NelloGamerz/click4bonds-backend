package com.click4bonds.app.Modules.DealConfirmation.Dto;

/**
 * The counterparty's own particulars, as the confirmation letter prints them.
 *
 * <p>Resolved once, inside the deal's transaction, from the party behind the
 * counterparty — the client's party for a back-office deal, the customer's for a
 * purchase. Every value is nullable: a party that has not recorded a PAN, an
 * address, or has no demat or bank account prints an em dash rather than a
 * guess.</p>
 *
 * <p>{@code pan} and {@code bankAccountNumber} are plaintext. They are what the
 * letter must show, so they travel on the document snapshot; they are never
 * logged.</p>
 */
public record CounterpartyDetails(
        String pan,
        String dpName,
        String dpId,
        String clientId,
        String bankName,
        String address,
        String bankBranchLocation,
        String bankIfsc,
        String bankAccountNumber) {
}
