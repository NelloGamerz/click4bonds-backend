// package com.click4bonds.app.Modules.Bond.Dto;

// import java.math.BigDecimal;
// import java.time.LocalDate;

// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

// import jakarta.validation.constraints.*;
// import lombok.Data;

// @Data
// public class CreateBondRequest {

//     @NotBlank
//     private String isin;

//     @NotBlank
//     private String name;

//     private String issuer;

//     private String description;

//     @NotNull
//     @DecimalMin("0.01")
//     private BigDecimal faceValue;

//     @NotNull
//     @DecimalMin("0")
//     private BigDecimal couponRate;

//     @NotNull
//     private CouponFrequency couponFrequency;

//     @NotNull
//     private LocalDate issueDate;

//     @NotNull
//     private LocalDate maturityDate;

//     @NotNull
//     @DecimalMin("0.01")
//     private BigDecimal sellingPrice;

//     @NotNull
//     @DecimalMin("0.01")
//     private BigDecimal minimumInvestment;

//     @NotNull
//     @Min(1)
//     private Long totalUnits;
// }

// package com.click4bonds.app.Modules.Bond.Dto;

// import java.math.BigDecimal;
// import java.time.LocalDate;

// import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

// import jakarta.validation.constraints.DecimalMin;
// import jakarta.validation.constraints.Min;
// import jakarta.validation.constraints.NotBlank;
// import jakarta.validation.constraints.NotNull;
// import lombok.Data;

// @Data
// public class CreateBondRequest {

//     @NotNull
//     private Integer serialNumber;

//     @NotBlank
//     private String name;

//     @NotNull
//     @DecimalMin("0")
//     private BigDecimal couponRate;

//     @NotNull
//     private SecurityType securityType;

//     @NotBlank
//     private String isin;

//     private String rating;

//     private String ratingAgency;

//     @NotNull
//     private LocalDate maturityDate;

//     private LocalDate putDate;

//     private LocalDate callDate;

//     @NotNull
//     @DecimalMin("0.01")
//     private BigDecimal price;

//     @DecimalMin("0")
//     private BigDecimal semiYtm;

//     @DecimalMin("0")
//     private BigDecimal annualYtm;

//     @DecimalMin("0")
//     private BigDecimal ytc;

//     private LocalDate ipDate;

//     @NotNull
//     @DecimalMin("0")
//     private BigDecimal quantumInLacs;

//     @NotNull
//     // @Min(1)
//     private String lotSize;
// }

package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBondRequest {

    // =========================
    // IDENTIFICATION
    // =========================

    /**
     * Can be null because the Excel sheet may not provide
     * a serial number for every bond.
     */
    private Integer serialNumber;

    @NotBlank
    @Size(max = 500)
    private String name;

    @NotBlank
    @Size(max = 12)
    private String isin;

    // =========================
    // CLASSIFICATION
    // =========================

    /**
     * Example:
     * Category 1 ( Gsec/ SDL )
     * Category 2 ( Corporate Bond )
     */
    @Size(max = 255)
    private String category;

    @NotNull
    private SecurityType securityType;

    @Size(max = 100)
    private String rating;

    @Size(max = 200)
    private String ratingAgency;

    // =========================
    // COUPON
    // =========================

    /**
     * Percentage value.
     *
     * Example:
     * 7.20 = 7.20%
     */
    @NotNull
    @DecimalMin("0")
    private BigDecimal couponRate;

    private CouponFrequency couponFrequency;

    /**
     * Examples:
     *
     * 09/02-09/08
     * 15/10 Ann
     * 31st of every month
     */
    @Size(max = 255)
    private String ipDateDescription;

    // =========================
    // MATURITY
    // =========================

    @NotNull
    private MaturityType maturityType;

    /**
     * Required for FIXED maturity bonds.
     */
    private LocalDate maturityDate;

    /**
     * Original maturity text.
     *
     * Examples:
     *
     * 26-09-2031 (2.5% on Each IP till 2027...)
     *
     * 9/11/2024 to 9/11/2033 (10% each year)
     *
     * Perp
     */
    @Size(max = 1500)
    private String maturityDescription;

    // =========================
    // PUT / CALL
    // =========================

    /**
     * Examples:
     *
     * NA
     * 31/Jan/33
     * blank
     */
    @Size(max = 255)
    private String putCallDescription;

    // =========================
    // PRICE
    // =========================

    /**
     * Price can be blank in Excel,
     * therefore this should NOT be @NotNull.
     */
    @DecimalMin("0.01")
    private BigDecimal price;

    // =========================
    // QUANTUM
    // =========================

    /**
     * Original value from Excel.
     *
     * Examples:
     * 3 Lakh
     * 1.50 Lakh
     * Any
     * 1 Bonds
     */
    @Size(max = 100)
    private String quantumDescription;

    /**
     * Normalized value in lakhs.
     *
     * Example:
     * 3 Lakh -> 3.00
     * 1.50 Lakh -> 1.50
     *
     * Can be null for:
     * Any
     * 1 Bonds
     */
    @DecimalMin("0")
    private BigDecimal quantumInLacs;

    // =========================
    // LOT SIZE
    // =========================

    /**
     * Original Excel value.
     *
     * Examples:
     * Demat
     * SGL
     * 1000 Lot
     * 10 Lakh Lot
     * 1 Crore Lot
     */
    @Size(max = 100)
    private String lotSizeDescription;

    /**
     * Normalized numeric lot size.
     *
     * Examples:
     * 1000 Lot -> 1000
     * 10 Lakh Lot -> 1000000
     *
     * NULL for DEMAT / SGL.
     */
    @DecimalMin("0")
    private BigDecimal lotSize;

    private LotSizeType lotSizeType;
}