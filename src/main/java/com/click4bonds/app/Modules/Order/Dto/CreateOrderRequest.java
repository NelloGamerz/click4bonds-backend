package com.click4bonds.app.Modules.Order.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A request to place an order for a bond.
 *
 * <p>Carries no price. The price is read from the bond by the server, exactly as
 * {@code DealConfirmationWriter} does when it strikes a deal — a client-supplied
 * price could disagree with the bond it is buying, and the figure the customer
 * is charged has to be the one the platform holds, not the one the caller
 * typed.</p>
 *
 * <p>Carries no buyer either: that is the authenticated account, taken from the
 * JWT by the controller, so an order cannot be placed in someone else's
 * name.</p>
 *
 * <p>Carries no per-lot quantity and no total, for the same reason
 * {@code CreateDealConfirmationRequest} does not: the lot size belongs to the
 * bond. {@link #quantity} is a <strong>number of lots</strong> — a bond trading
 * in lots of 100 bought with {@code quantity: 1} is 100 units — and the server
 * works the units out as {@code quantity * bond.lotSize}.</p>
 */
public record CreateOrderRequest(

        @NotBlank(message = "Bond ISIN is required")
        String bondIsin,

        /**
         * How many lots to buy, not how many units. The units in a lot come from
         * the bond.
         */
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than 0")
        Long quantity
) {
}
