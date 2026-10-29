package com.click4bonds.app.Modules.DealConfirmation.Enums;

/**
 * How a back-office deal settles, and therefore which particulars the
 * confirmation letter prints on both sides.
 *
 * <ul>
 *   <li>{@code ICCL} — cleared through the clearing corporation. Our CM
 *       particulars and settlement number; the counterparty's PAN and demat
 *       identifiers.</li>
 *   <li>{@code DEMAT} — a direct demat transfer. Our DP and bank particulars;
 *       the counterparty's PAN and bank particulars.</li>
 *   <li>{@code NSCCL} — cleared through NSE Clearing. Our bank particulars and
 *       settlement number; the counterparty's PAN and CM particulars.</li>
 * </ul>
 *
 * <p>Chosen by the employee on a back-office deal; null on a customer-placed
 * deal, which keeps the configured default.</p>
 */
public enum DeliveryMode {

    ICCL,

    DEMAT,

    NSCCL
}
