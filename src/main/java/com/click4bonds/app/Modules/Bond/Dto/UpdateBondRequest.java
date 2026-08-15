package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class UpdateBondRequest {

    @Size(min = 1, max = 255)
    private String name;

    private String issuer;

    private String description;

    @DecimalMin("0.01")
    private BigDecimal faceValue;

    @DecimalMin("0")
    private BigDecimal couponRate;

    private CouponFrequency couponFrequency;

    private LocalDate issueDate;

    private LocalDate maturityDate;

    @DecimalMin("0.01")
    private BigDecimal sellingPrice;

    @DecimalMin("0.01")
    private BigDecimal minimumInvestment;
}
