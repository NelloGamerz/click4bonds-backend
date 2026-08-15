package com.click4bonds.app.Modules.Order.Dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotNull
    private UUID bondId;

    @NotNull
    @Min(1)
    private Long quantity;
}
