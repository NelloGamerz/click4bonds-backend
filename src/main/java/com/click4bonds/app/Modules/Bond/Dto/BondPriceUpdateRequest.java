package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BondPriceUpdateRequest {

    @NotBlank
    @Size(max = 12)
    private String isin;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;
}