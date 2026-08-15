package com.click4bonds.app.Modules.Order.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.Bond.Enums.BondOrderStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BondOrderResponse {

    private UUID id;

    private String orderNumber;

    private UUID bondId;

    private String bondName;

    private Long quantity;

    private BigDecimal pricePerUnit;

    private BigDecimal totalAmount;

    private BondOrderStatus status;

    private Instant createdAt;
}
