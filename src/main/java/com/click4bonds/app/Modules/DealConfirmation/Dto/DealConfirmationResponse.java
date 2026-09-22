package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;

import lombok.Builder;
import lombok.Data;

/**
 * A created deal, as returned to the customer.
 *
 * <p>Follows the shaping of {@code BondOrderResponse}: the internal id, the
 * customer-facing reference, the bond it belongs to, and the money. Nothing
 * about inventory internals, the idempotency key or the database is exposed.</p>
 */
@Data
@Builder
public class DealConfirmationResponse {

    private UUID dealConfirmationId;

    private String dealReference;

    private UUID bondId;

    private String bondName;

    private String isin;

    private Long quantityPerLot;

    private Long numberOfLots;

    private Long totalQuantity;

    /** NULL when the bond has no price recorded. */
    private BigDecimal pricePerUnit;

    /** NULL when the bond has no price recorded. */
    private BigDecimal totalAmount;

    private DealConfirmationStatus status;

    private Instant createdAt;
}
