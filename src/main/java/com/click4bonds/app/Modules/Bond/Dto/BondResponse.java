// // package com.click4bonds.app.Modules.Bond.Dto;

// // import java.math.BigDecimal;
// // import java.time.Instant;
// // import java.time.LocalDate;
// // import java.util.UUID;

// // import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
// // import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

// // import lombok.Builder;
// // import lombok.Data;

// // @Data
// // @Builder
// // public class BondResponse {

// //     private UUID id;

// //     private String isin;

// //     private String name;

// //     private String issuer;

// //     private String description;

// //     private BigDecimal faceValue;

// //     private BigDecimal couponRate;

// //     private CouponFrequency couponFrequency;

// //     private LocalDate issueDate;

// //     private LocalDate maturityDate;

// //     private BigDecimal sellingPrice;

// //     private BigDecimal minimumInvestment;

// //     private Long totalUnits;

// //     private Long availableUnits;

// //     private BondStatus status;

// //     private Instant createdAt;

// //     private Instant updatedAt;
// // }

// package com.click4bonds.app.Modules.Bond.Dto;

// import java.math.BigDecimal;
// import java.time.Instant;
// import java.time.LocalDate;
// import java.util.UUID;

// import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
// import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

// import lombok.Builder;
// import lombok.Data;

// @Data
// @Builder
// public class BondResponse {

//     private UUID id;

//     private Integer serialNumber;

//     private String name;

//     private BigDecimal couponRate;

//     private SecurityType securityType;

//     private String isin;

//     private String rating;

//     private String ratingAgency;

//     private LocalDate maturityDate;

//     private LocalDate putDate;

//     private LocalDate callDate;

//     private BigDecimal price;

//     private BigDecimal semiYtm;

//     private BigDecimal annualYtm;

//     private BigDecimal ytc;

//     private LocalDate ipDate;

//     private BigDecimal quantumInLacs;

//     private String lotSize;

//     private BondStatus status;

//     private Instant createdAt;

//     private Instant updatedAt;
// }

package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BondResponse {

    private UUID id;

    // =========================
    // IDENTIFICATION
    // =========================

    private Integer serialNumber;

    private String name;

    private String isin;

    // =========================
    // CLASSIFICATION
    // =========================

    private String category;

    private SecurityType securityType;

    private String rating;

    private String ratingAgency;

    // =========================
    // COUPON
    // =========================

    /**
     * Example:
     * 7.20 means 7.20%
     */
    private BigDecimal couponRate;

    private CouponFrequency couponFrequency;

    /**
     * Examples:
     * 09/02-09/08
     * 15/10 Ann
     * 31st of every month
     */
    private String ipDateDescription;

    // =========================
    // MATURITY
    // =========================

    private MaturityType maturityType;

    /**
     * Normalized maturity date.
     *
     * Example:
     * 9/Aug/27 -> 2027-08-09
     */
    private LocalDate maturityDate;

    /**
     * Original maturity information from Excel.
     *
     * Examples:
     *
     * "26-09-2031 (2.5% on Each IP till 2027...)"
     *
     * "9/11/2024 to 9/11/2033 (10% each year)"
     *
     * "Perp"
     */
    private String maturityDescription;

    // =========================
    // PUT / CALL
    // =========================

    /**
     * Examples:
     *
     * NA
     * 31/Jan/33
     * null
     */
    private String putCallDescription;

    // =========================
    // PRICE
    // =========================

    /**
     * Can be null when price is not available.
     */
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
    private String quantumDescription;

    /**
     * Normalized value in lakhs.
     */
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
    private String lotSizeDescription;

    /**
     * Normalized numeric lot size.
     *
     * NULL for DEMAT / SGL.
     */
    private BigDecimal lotSize;

    private LotSizeType lotSizeType;

    // =========================
    // STATUS
    // =========================

    private BondStatus status;

    // =========================
    // AUDIT
    // =========================

    private Instant createdAt;

    private Instant updatedAt;
}