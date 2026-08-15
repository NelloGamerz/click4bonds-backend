package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BondResponse {

    private UUID id;

    private String isin;

    private String name;

    private String issuer;

    private String description;

    private BigDecimal faceValue;

    private BigDecimal couponRate;

    private CouponFrequency couponFrequency;

    private LocalDate issueDate;

    private LocalDate maturityDate;

    private BigDecimal sellingPrice;

    private BigDecimal minimumInvestment;

    private Long totalUnits;

    private Long availableUnits;

    private BondStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
