package com.click4bonds.app.Modules.Admin.Dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A request to create a deal for a client.
 *
 * <p>The employee names the client and the bond; the per-lot quantity and the
 * total are still read and computed by the server, exactly as on the customer
 * path.</p>
 */
public record CreateClientDealRequest(

        @NotNull(message = "Client id is required")
        UUID clientId,

        @NotNull(message = "Bond id is required")
        UUID bondId,

        @NotNull(message = "Number of lots is required")
        @Positive(message = "Number of lots must be greater than 0")
        Long numberOfLots,

        /**
         * The date the deal is struck, or null for today. It fixes the value date
         * and the month of the deal reference.
         */
        LocalDate dealDate,

        /**
         * Whether the confirmation letter is emailed to the client. False when
         * omitted: a back-office deal is only mailed when the employee asks for
         * it.
         */
        boolean sendEmail) {
}
