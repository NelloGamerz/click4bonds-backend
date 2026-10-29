package com.click4bonds.app.Modules.DealConfirmation.Enums;

/**
 * Which side of the deal we are on.
 *
 * <p>{@code SELL} is a customer buying from us (our sale); {@code BUY} is a
 * back-office employee buying from a client (our purchase). The side fixes the
 * letter of the deal reference ({@code S}/{@code B}) and the transaction type
 * the confirmation letter prints.</p>
 */
public enum DealSide {

    BUY,

    SELL
}
