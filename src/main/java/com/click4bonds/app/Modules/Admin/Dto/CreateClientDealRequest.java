package com.click4bonds.app.Modules.Admin.Dto;

import java.time.LocalDate;
import java.util.UUID;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
import com.click4bonds.app.Modules.Party.Enums.Depository;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
        boolean sendEmail,

        /**
         * The client's bank account the employee chose, or null when none was
         * chosen. Must belong to the client's party, which the writer checks.
         */
        UUID bankAccountId,

        /** The client's demat account the employee chose, on the same terms. */
        UUID dematAccountId,

        /**
         * Whether we are selling the bond to the client or buying it from them.
         * It fixes the reference letter ({@code S}/{@code B}) and the
         * transaction type the letter prints.
         */
        @NotNull(message = "Side is required")
        DealSide side,

        /** Which settlement route the letter describes. */
        @NotNull(message = "Mode of delivery is required")
        DeliveryMode modeOfDelivery,

        /**
         * The depository whose demat identifiers the letter prints. Required for
         * a {@code DEMAT} buy, ignored otherwise.
         */
        Depository depository,

        /**
         * The clearing house's settlement number, supplied by the employee.
         *
         * <p>Required for {@code ICCL} and {@code NSCCL}, which print it in our
         * particulars; {@code DEMAT} has no settlement number and ignores it.</p>
         */
        @Size(max = 64, message = "Settlement number must be at most 64 characters")
        String settlementNumber) {
}
