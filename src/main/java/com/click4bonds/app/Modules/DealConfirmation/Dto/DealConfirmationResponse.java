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

    /**
     * Every figure the confirmation letter prints — accrued interest, quantum,
     * principal, stamp duty or TDS, the total consideration — so the frontend
     * can show the customer what they bought without waiting for the document.
     *
     * <p><strong>Null when the letter cannot be built.</strong> A deal with no
     * price or no accrued interest is refused by
     * {@code DealConfirmationSheetValuesFactory}, because a letter missing its
     * consideration is worse than no letter. The purchase itself is unaffected —
     * these figures are a presentation of the deal, never a precondition of it —
     * so the response carries none rather than failing the request.</p>
     *
     * <p>Also null on a replayed request. The figures are computed inside the
     * transaction that created the deal, from the interest schedule as it stood
     * then; reconstructing them for a deal read back outside that transaction
     * would risk printing a different number from the one the customer was
     * shown the first time.</p>
     */
    private DealConfirmationSheetValues letterValues;
}
