package com.click4bonds.app.Modules.DealConfirmation.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A request to buy a bond.
 *
 * <p>Carries no customer: the buyer is always the authenticated user, taken from
 * the JWT by the controller. There is deliberately no field a client could set
 * to buy on someone else's behalf.</p>
 *
 * <p>Also carries no total quantity. The server computes
 * {@code quantityPerLot * numberOfLots} — a client-supplied total could disagree
 * with the parts it is made of.</p>
 */
public record CreateDealConfirmationRequest(

        /**
         * ISIN of the bond to buy.
         *
         * <p>Length follows the existing bond API, which bounds ISINs at 12
         * characters; the value is upper-cased before lookup, matching
         * {@code BondService.findBond}.</p>
         */
        @NotBlank(message = "ISIN is required")
        @Size(max = 12, message = "ISIN must be at most 12 characters")
        String isin,

        @NotNull(message = "Quantity per lot is required")
        @Positive(message = "Quantity per lot must be greater than 0")
        Long quantityPerLot,

        @NotNull(message = "Number of lots is required")
        @Positive(message = "Number of lots must be greater than 0")
        Long numberOfLots
) {
}
