package com.click4bonds.app.Modules.Order.Dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.click4bonds.app.Modules.Bond.Enums.BondOrderStatus;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;

import lombok.Builder;
import lombok.Data;

/**
 * The outcome of paying an order.
 *
 * <p>Describes the three things one payment produced: the order's new state, the
 * deal confirmation written against the bond, and the holding the units landed
 * in. Shaped like {@link BondOrderResponse} and
 * {@code DealConfirmationResponse}, with nothing about inventory internals or
 * the database exposed.</p>
 */
@Data
@Builder
public class BondOrderPaymentResponse {

    private UUID orderId;

    private String orderNumber;

    private BondOrderStatus status;

    private UUID bondId;

    private String bondName;

    private String isin;

    private Long quantity;

    private BigDecimal pricePerUnit;

    private BigDecimal totalAmount;

    /** The deal written by this payment; NULL only if none could be resolved. */
    private UUID dealConfirmationId;

    private String dealReference;

    private DealConfirmationStatus dealStatus;

    /**
     * Reference minted by the dummy payment step. Prefixed {@code PAY-DUMMY-} so
     * a value in a response or a log can never be mistaken for a real
     * settlement.
     */
    private String paymentReference;

    /** The customer's total holding in this bond after this payment. */
    private Long holdingQuantity;

    /**
     * True when this request was a duplicate answered with the deal the first
     * one created, and therefore bought nothing further.
     */
    private boolean replayed;
}
