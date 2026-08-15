package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateBondRequest {

    @NotBlank
    private String isin;

    @NotBlank
    private String name;

    private String issuer;

    private String description;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal faceValue;

    @NotNull
    @DecimalMin("0")
    private BigDecimal couponRate;

    @NotNull
    private CouponFrequency couponFrequency;

    @NotNull
    private LocalDate issueDate;

    @NotNull
    private LocalDate maturityDate;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal sellingPrice;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal minimumInvestment;

    @NotNull
    @Min(1)
    private Long totalUnits;
}
