// package com.click4bonds.app.Modules.Bond.Service;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.util.ArrayList;
// import java.util.Comparator;
// import java.util.List;

// import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
// import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
// import com.click4bonds.app.Modules.Bond.Models.Bond;

// import lombok.RequiredArgsConstructor;

// @Service
// @RequiredArgsConstructor
// public class BondCashFlowService {

//     /**
//      * Standard bond face value.
//      *
//      * All YTM calculations are performed
//      * on ₹100 face value.
//      */
//     private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);

//     public List<XirrCalculator.CashFlow> generateCashFlows(
//             Bond bond,
//             LocalDate calculationDate) {

//         validateBond(bond);

//         BigDecimal price = bond.getPrice();

//         List<XirrCalculator.CashFlow> cashFlows = new ArrayList<>();

//         /*
//          * Initial investment.
//          *
//          * Example:
//          * Price = 97.50
//          *
//          * Cash flow:
//          * 23-Aug-2026 → -97.50
//          */
//         cashFlows.add(
//                 new XirrCalculator.CashFlow(
//                         calculationDate,
//                         price.negate()));

//         /*
//          * Perpetual bonds don't have
//          * a maturity principal repayment.
//          */
//         if (bond.getMaturityType() == MaturityType.PERPETUAL) {

//             throw new IllegalArgumentException(
//                     "Normal YTM cannot be calculated " +
//                             "for a perpetual bond");
//         }

//         LocalDate maturityDate = bond.getMaturityDate();

//         if (maturityDate == null) {
//             throw new IllegalArgumentException(
//                     "Maturity date is required");
//         }

//         /*
//          * Generate coupon dates.
//          */
//         List<LocalDate> couponDates = generateCouponDates(
//                 bond,
//                 calculationDate,
//                 maturityDate);

//         /*
//          * Generate coupon cash flows.
//          */
//         BigDecimal couponAmount = calculateCouponAmount(bond);

//         for (LocalDate couponDate : couponDates) {

//             BigDecimal amount = couponAmount;

//             /*
//              * On maturity date:
//              *
//              * Coupon + ₹100 principal
//              */
//             if (couponDate.equals(maturityDate)) {

//                 amount = amount.add(FACE_VALUE);
//             }

//             cashFlows.add(
//                     new XirrCalculator.CashFlow(
//                             couponDate,
//                             amount));
//         }

//         return cashFlows.stream()
//                 .sorted(
//                         Comparator.comparing(
//                                 XirrCalculator.CashFlow::date))
//                 .toList();
//     }

//     private BigDecimal calculateCouponAmount(
//             Bond bond) {

//         BigDecimal annualCoupon = FACE_VALUE
//                 .multiply(bond.getCouponRate())
//                 .divide(
//                         BigDecimal.valueOf(100),
//                         10,
//                         RoundingMode.HALF_UP);

//         return switch (bond.getCouponFrequency()) {

//             case YEARLY ->
//                 annualCoupon;

//             case HALF_YEARLY ->
//                 annualCoupon.divide(
//                         BigDecimal.valueOf(2),
//                         10,
//                         RoundingMode.HALF_UP);

//             case QUARTERLY ->
//                 annualCoupon.divide(
//                         BigDecimal.valueOf(4),
//                         10,
//                         RoundingMode.HALF_UP);

//             case MONTHLY ->
//                 annualCoupon.divide(
//                         BigDecimal.valueOf(12),
//                         10,
//                         RoundingMode.HALF_UP);

//             default ->
//                 throw new IllegalArgumentException(
//                         "Unsupported coupon frequency: "
//                                 + bond.getCouponFrequency());
//         };
//     }

//     private void validateBond(Bond bond) {

//         if (bond == null) {
//             throw new IllegalArgumentException(
//                     "Bond cannot be null");
//         }

//         if (bond.getPrice() == null) {
//             throw new IllegalArgumentException(
//                     "Bond price is not available");
//         }

//         if (bond.getCouponRate() == null) {
//             throw new IllegalArgumentException(
//                     "Coupon rate is required");
//         }

//         if (bond.getCouponFrequency() == null) {
//             throw new IllegalArgumentException(
//                     "Coupon frequency is required");
//         }
//     }

//     private List<LocalDate> generateCouponDates(
//             Bond bond,
//             LocalDate calculationDate,
//             LocalDate maturityDate) {

//         return switch (bond.getCouponFrequency()) {

//             case YEARLY ->
//                 generateAnnualDates(
//                         bond,
//                         calculationDate,
//                         maturityDate);

//             case HALF_YEARLY ->
//                 generateSemiAnnualDates(
//                         bond,
//                         calculationDate,
//                         maturityDate);

//             case QUARTERLY ->
//                 generateQuarterlyDates(
//                         bond,
//                         calculationDate,
//                         maturityDate);

//             case MONTHLY ->
//                 generateMonthlyDates(
//                         bond,
//                         calculationDate,
//                         maturityDate);

//             default ->
//                 throw new IllegalArgumentException(
//                         "Unsupported coupon frequency");
//         };
//     }

//     /*
//      * These methods will be implemented
//      * using ipDateDescription.
//      */

//     private List<LocalDate> generateAnnualDates(
//             Bond bond,
//             LocalDate calculationDate,
//             LocalDate maturityDate) {

//         // TODO
//         return new ArrayList<>();
//     }

//     private List<LocalDate> generateSemiAnnualDates(
//             Bond bond,
//             LocalDate calculationDate,
//             LocalDate maturityDate) {

//         // TODO
//         return new ArrayList<>();
//     }

//     private List<LocalDate> generateQuarterlyDates(
//             Bond bond,
//             LocalDate calculationDate,
//             LocalDate maturityDate) {

//         // TODO
//         return new ArrayList<>();
//     }

//     private List<LocalDate> generateMonthlyDates(
//             Bond bond,
//             LocalDate calculationDate,
//             LocalDate maturityDate) {

//         // TODO
//         return new ArrayList<>();
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BondCashFlowService {

    /**
     * Standard bond face value.
     *
     * All YTM calculations are performed
     * on ₹100 face value.
     */
    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);

    private final CouponDateGenerator couponDateGenerator;

    /**
     * Generates all future cash flows required
     * for YTM/XIRR calculation.
     *
     * Initial cash flow:
     *
     * calculationDate -> -Bond.price
     *
     * Future cash flows:
     *
     * coupon dates -> coupon amount
     *
     * maturity date -> coupon + ₹100 principal
     */
    public List<XirrCalculator.CashFlow> generateCashFlows(
            Bond bond,
            LocalDate calculationDate) {

        validateBond(bond);

        BigDecimal price = bond.getPrice();

        List<XirrCalculator.CashFlow> cashFlows = new ArrayList<>();

        /*
         * Initial investment.
         *
         * Example:
         *
         * Price = 97.50
         *
         * 24-Aug-2026 -> -97.50
         */
        cashFlows.add(
                new XirrCalculator.CashFlow(
                        calculationDate,
                        price.negate()));

        /*
         * Perpetual bonds don't have a maturity
         * principal repayment.
         *
         * Normal YTM is therefore not applicable.
         */
        if (bond.getMaturityType() == MaturityType.PERPETUAL) {

            throw new IllegalArgumentException(
                    "Normal YTM cannot be calculated " +
                            "for a perpetual bond");
        }

        LocalDate maturityDate = bond.getMaturityDate();

        if (maturityDate == null) {

            throw new IllegalArgumentException(
                    "Maturity date is required");
        }

        /*
         * Generate all future coupon dates.
         *
         * CouponDateGenerator is responsible for
         * parsing ipDateDescription.
         */
        List<LocalDate> couponDates = couponDateGenerator.generate(
                bond,
                calculationDate);

        /*
         * Calculate coupon amount per ₹100
         * face value.
         */
        BigDecimal couponAmount = calculateCouponAmount(bond);

        /*
         * Create coupon cash flows.
         */
        for (LocalDate couponDate : couponDates) {

            BigDecimal amount = couponAmount;

            /*
             * On maturity:
             *
             * Coupon + ₹100 principal
             */
            if (couponDate.equals(maturityDate)) {

                amount = amount.add(FACE_VALUE);
            }

            cashFlows.add(
                    new XirrCalculator.CashFlow(
                            couponDate,
                            amount));
        }

        /*
         * Always sort chronologically.
         */
        return cashFlows.stream()
                .sorted(
                        Comparator.comparing(
                                XirrCalculator.CashFlow::date))
                .toList();
    }

    /**
     * Calculates coupon amount based on
     * ₹100 face value.
     *
     * Example:
     *
     * Coupon = 8.79%
     *
     * Annual:
     *
     * 100 × 8.79 / 100
     * = ₹8.79
     *
     * Half-yearly:
     *
     * ₹8.79 / 2
     * = ₹4.395
     */
    private BigDecimal calculateCouponAmount(
            Bond bond) {

        BigDecimal annualCoupon = FACE_VALUE
                .multiply(bond.getCouponRate())
                .divide(
                        BigDecimal.valueOf(100),
                        10,
                        RoundingMode.HALF_UP);

        return switch (bond.getCouponFrequency()) {

            case YEARLY ->
                annualCoupon;

            case HALF_YEARLY ->
                annualCoupon.divide(
                        BigDecimal.valueOf(2),
                        10,
                        RoundingMode.HALF_UP);

            case QUARTERLY ->
                annualCoupon.divide(
                        BigDecimal.valueOf(4),
                        10,
                        RoundingMode.HALF_UP);

            case MONTHLY ->
                annualCoupon.divide(
                        BigDecimal.valueOf(12),
                        10,
                        RoundingMode.HALF_UP);

            default ->
                throw new IllegalArgumentException(
                        "Unsupported coupon frequency: "
                                + bond.getCouponFrequency());
        };
    }

    /**
     * Validates the minimum information required
     * to generate cash flows.
     */
    private void validateBond(Bond bond) {

        if (bond == null) {

            throw new IllegalArgumentException(
                    "Bond cannot be null"
            );
        }

        if (bond.getPrice() == null) {

            throw new IllegalArgumentException(
                    "Bond price is not available"
            );
        }

        if (bond.getCouponRate() == null) {

            throw new IllegalArgumentException(
                    "Coupon rate is required"
            );
        }

        if (bond.getCouponFrequency() == null) {

            throw new IllegalArgumentException(
                    "Coupon frequency is required"
            );
        }

        if (bond.getMaturityType() == null) {

            throw new IllegalArgumentException(
                    "Maturity type is required"
            );
        }
    }
}
