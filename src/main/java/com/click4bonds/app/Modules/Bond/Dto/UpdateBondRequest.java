// package com.click4bonds.app.Modules.Bond.Dto;

// import java.math.BigDecimal;
// import java.time.LocalDate;

// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

// import jakarta.validation.constraints.DecimalMin;
// import jakarta.validation.constraints.Size;
// import lombok.Data;

// @Data
// public class UpdateBondRequest {

//     @Size(min = 1, max = 255)
//     private String name;

//     private String issuer;

//     private String description;

//     @DecimalMin("0.01")
//     private BigDecimal faceValue;

//     @DecimalMin("0")
//     private BigDecimal couponRate;

//     private CouponFrequency couponFrequency;

//     private LocalDate issueDate;

//     private LocalDate maturityDate;

//     @DecimalMin("0.01")
//     private BigDecimal sellingPrice;

//     @DecimalMin("0.01")
//     private BigDecimal minimumInvestment;
// }

// package com.click4bonds.app.Modules.Bond.Dto;

// import java.math.BigDecimal;
// import java.time.LocalDate;

// import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

// import jakarta.validation.constraints.DecimalMin;
// import jakarta.validation.constraints.Size;
// import lombok.Data;

// @Data
// public class UpdateBondRequest {

//     private Integer serialNumber;

//     @Size(min = 1, max = 255)
//     private String name;

//     @DecimalMin("0")
//     private BigDecimal couponRate;

//     private SecurityType securityType;

//     private String rating;

//     private String ratingAgency;

//     private LocalDate maturityDate;

//     private LocalDate putDate;

//     private LocalDate callDate;

//     @DecimalMin("0.01")
//     private BigDecimal price;

//     @DecimalMin("0")
//     private BigDecimal semiYtm;

//     @DecimalMin("0")
//     private BigDecimal annualYtm;

//     @DecimalMin("0")
//     private BigDecimal ytc;

//     private LocalDate ipDate;

//     @DecimalMin("0")
//     private BigDecimal quantumInLacs;

//     // @DecimalMin("1")
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
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBondRequest {

    // =========================
    // IDENTIFICATION
    // =========================

    private Integer serialNumber;

    @Size(min = 1, max = 500)
    private String name;

    // =========================
    // CLASSIFICATION
    // =========================

    @Size(max = 255)
    private String category;

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

    private MaturityType maturityType;

    private LocalDate maturityDate;

    /**
     * Original maturity value from Excel.
     *
     * Examples:
     *
     * 9/Aug/27
     * Perp
     * 26-09-2031 (2.5% on Each IP till 2027...)
     * 9/11/2024 to 9/11/2033 (10% each year)
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
     * Can be null because Excel price can be blank.
     */
    @DecimalMin("0.01")
    private BigDecimal price;

    // =========================
    // QUANTUM
    // =========================

    /**
     * Original Excel value.
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
     * Normalized quantum in lakhs.
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
     * NULL for DEMAT / SGL.
     */
    @DecimalMin("0")
    private BigDecimal lotSize;

    private LotSizeType lotSizeType;
}