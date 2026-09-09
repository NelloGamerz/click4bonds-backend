// package com.click4bonds.app.Modules.Bond.Service;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.util.Comparator;
// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Map;

// import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
// import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
// import com.click4bonds.app.Modules.Bond.Models.Bond;

// @Service
// public class CouponCalculationServiceImpl implements CouponCalculationService {

//     private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
//     private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
//     private static final int CALCULATION_SCALE = 10;

//     @Override
//     public List<CouponPayment> calculateCoupons(
//             Bond bond,
//             List<LocalDate> couponDates,
//             List<PrincipalRepayment> principalRepayments,
//             LocalDate calculationDate) {
//         validateInput(bond, couponDates, principalRepayments, calculationDate);

//         BigDecimal couponRate = bond.getCouponRate();
//         if (couponRate == null) {
//             throw new IllegalArgumentException("Coupon rate is required");
//         }
//         if (couponRate.signum() < 0) {
//             throw new IllegalArgumentException("Coupon rate cannot be negative");
//         }

//         BigDecimal frequencyDivisor = frequencyDivisor(bond.getCouponFrequency());
//         Map<LocalDate, BigDecimal> repaymentsByDate = repaymentsByDate(principalRepayments);
//         BigDecimal outstandingPrincipal = initialOutstandingPrincipal(principalRepayments);
//         List<LocalDate> paymentDates = couponDates.stream()
//                 .filter(date -> date.isAfter(calculationDate))
//                 .filter(date -> bond.getMaturityDate() == null || !date.isAfter(bond.getMaturityDate()))
//                 .distinct()
//                 .sorted()
//                 .toList();

//         List<CouponPayment> payments = new java.util.ArrayList<>();
//         int repaymentIndex = 0;
//         List<LocalDate> repaymentDates = repaymentsByDate.keySet().stream().sorted().toList();
//         for (LocalDate paymentDate : paymentDates) {
//             while (repaymentIndex < repaymentDates.size()
//                     && repaymentDates.get(repaymentIndex).isBefore(paymentDate)) {
//                 outstandingPrincipal = outstandingPrincipal.subtract(
//                         repaymentsByDate.get(repaymentDates.get(repaymentIndex)));
//                 repaymentIndex++;
//             }

//             BigDecimal couponAmount = outstandingPrincipal
//                     .multiply(couponRate)
//                     .divide(HUNDRED.multiply(frequencyDivisor), CALCULATION_SCALE, RoundingMode.HALF_UP);
//             payments.add(new CouponPayment(paymentDate, couponAmount, outstandingPrincipal));

//             if (repaymentIndex < repaymentDates.size()
//                     && repaymentDates.get(repaymentIndex).equals(paymentDate)) {
//                 outstandingPrincipal = outstandingPrincipal.subtract(repaymentsByDate.get(paymentDate));
//                 repaymentIndex++;
//             }
//         }
//         return payments;
//     }

//     private Map<LocalDate, BigDecimal> repaymentsByDate(List<PrincipalRepayment> repayments) {
//         Map<LocalDate, BigDecimal> repaymentsByDate = new LinkedHashMap<>();
//         for (PrincipalRepayment repayment : repayments) {
//             if (repayment == null || repayment.date() == null
//                     || repayment.principalAmount() == null || repayment.remainingPrincipal() == null) {
//                 throw new IllegalArgumentException("Principal repayments must contain complete values");
//             }
//             if (repayment.principalAmount().signum() < 0) {
//                 throw new IllegalArgumentException("Principal repayment cannot be negative");
//             }
//                 repaymentsByDate.put(repayment.date(), repaymentsByDate
//                     .getOrDefault(repayment.date(), BigDecimal.ZERO)
//                     .add(repayment.principalAmount()));
//         }
//         return repaymentsByDate;
//     }

//     private BigDecimal initialOutstandingPrincipal(List<PrincipalRepayment> repayments) {
//         if (repayments.isEmpty()) {
//             return FACE_VALUE;
//         }
//         PrincipalRepayment firstRepayment = repayments.stream()
//             .min(Comparator.comparing(repayment -> repayment.date()))
//                 .orElseThrow();
//         return firstRepayment.remainingPrincipal().add(firstRepayment.principalAmount());
//     }

//     private BigDecimal frequencyDivisor(CouponFrequency frequency) {
//         if (frequency == null) {
//             throw new IllegalArgumentException("Coupon frequency is required");
//         }
//         return switch (frequency) {
//             case MONTHLY -> BigDecimal.valueOf(12);
//             case QUARTERLY -> BigDecimal.valueOf(4);
//             case HALF_YEARLY -> BigDecimal.valueOf(2);
//             case YEARLY -> BigDecimal.ONE;
//             case AT_MATURITY -> throw new IllegalArgumentException(
//                     "Unsupported coupon frequency: AT_MATURITY");
//         };
//     }

//     private void validateInput(
//             Bond bond,
//             List<LocalDate> couponDates,
//             List<PrincipalRepayment> principalRepayments,
//             LocalDate calculationDate) {
//         if (bond == null) {
//             throw new IllegalArgumentException("Bond cannot be null");
//         }
//         if (couponDates == null) {
//             throw new IllegalArgumentException("Coupon dates cannot be null");
//         }
//         if (principalRepayments == null) {
//             throw new IllegalArgumentException("Principal repayments cannot be null");
//         }
//         if (calculationDate == null) {
//             throw new IllegalArgumentException("Calculation date cannot be null");
//         }
//         if (couponDates.stream().anyMatch(date -> date == null)) {
//             throw new IllegalArgumentException("Coupon dates cannot contain null");
//         }
//     }
// }

// package com.click4bonds.app.Modules.Bond.Service;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.util.ArrayList;
// import java.util.Comparator;
// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Map;

// import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
// import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
// import com.click4bonds.app.Modules.Bond.Models.Bond;

// @Service
// public class CouponCalculationServiceImpl implements CouponCalculationService {

//     private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
//     private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
//     private static final BigDecimal ZERO = BigDecimal.ZERO;

//     private static final int CALCULATION_SCALE = 10;

//     @Override
//     public List<CouponPayment> calculateCoupons(
//             Bond bond,
//             List<LocalDate> couponDates,
//             List<PrincipalRepayment> principalRepayments,
//             LocalDate calculationDate) {

//         validateInput(
//                 bond,
//                 couponDates,
//                 principalRepayments,
//                 calculationDate);

//         /*
//          * ============================================================
//          * COUPON RATE
//          * ============================================================
//          *
//          * The coupon rate ALWAYS comes from the bond.
//          *
//          * Example:
//          *
//          * Coupon rate = 10%
//          *
//          * This does NOT change when amortization starts.
//          *
//          * Amortization percentage controls principal repayment only.
//          *
//          * Example:
//          *
//          * Principal = 100
//          * Coupon rate = 10%
//          * Amortization = 20%
//          *
//          * Coupon before amortization:
//          *
//          * 100 × 10% = 10
//          *
//          * After 20 principal is repaid:
//          *
//          * 80 × 10% = 8
//          * ============================================================
//          */
//         BigDecimal couponRate = bond.getCouponRate();

//         if (couponRate == null) {
//             throw new IllegalArgumentException(
//                     "Coupon rate is required");
//         }

//         if (couponRate.signum() < 0) {
//             throw new IllegalArgumentException(
//                     "Coupon rate cannot be negative");
//         }

//         /*
//          * ============================================================
//          * COUPON FREQUENCY
//          * ============================================================
//          */
//         BigDecimal frequencyDivisor = frequencyDivisor(bond.getCouponFrequency());

//         /*
//          * ============================================================
//          * PRINCIPAL REPAYMENTS
//          * ============================================================
//          *
//          * PrincipalRepaymentService has already interpreted:
//          *
//          * 20% Monthly
//          * 10% Quarterly
//          * 20% Yearly
//          * etc.
//          *
//          * This service does NOT reinterpret those percentages.
//          *
//          * It only uses the actual principal repayment amounts.
//          * ============================================================
//          */
//         Map<LocalDate, BigDecimal> repaymentsByDate = repaymentsByDate(principalRepayments);

//         /*
//          * ============================================================
//          * DETERMINE OUTSTANDING PRINCIPAL AT CALCULATION DATE
//          * ============================================================
//          *
//          * Because PrincipalRepaymentService returns only future
//          * repayments, the first future repayment contains enough
//          * information to determine the outstanding principal before
//          * that repayment:
//          *
//          * remainingPrincipal + currentRepayment
//          *
//          * Example:
//          *
//          * Original principal = 100
//          *
//          * First future repayment = 20
//          * Remaining after repayment = 80
//          *
//          * Therefore:
//          *
//          * outstanding before repayment = 80 + 20 = 100
//          * ============================================================
//          */
//         BigDecimal outstandingPrincipal = initialOutstandingPrincipal(principalRepayments);

//         /*
//          * ============================================================
//          * COUPON PAYMENT DATES
//          * ============================================================
//          */
//         List<LocalDate> paymentDates = couponDates.stream()
//                 .filter(date -> date != null)
//                 .filter(date -> date.isAfter(calculationDate))
//                 .filter(date -> bond.getMaturityDate() == null
//                         || !date.isAfter(bond.getMaturityDate()))
//                 .distinct()
//                 .sorted()
//                 .toList();

//         /*
//          * ============================================================
//          * GENERATE COUPONS
//          * ============================================================
//          */
//         List<CouponPayment> payments = new ArrayList<>();

//         List<LocalDate> repaymentDates = repaymentsByDate.keySet()
//                 .stream()
//                 .sorted()
//                 .toList();

//         int repaymentIndex = 0;

//         for (LocalDate paymentDate : paymentDates) {

//             /*
//              * --------------------------------------------------------
//              * APPLY ALL PRINCIPAL REPAYMENTS BEFORE THIS COUPON DATE
//              * --------------------------------------------------------
//              *
//              * If a repayment happened before the coupon date,
//              * coupon must be calculated on the reduced principal.
//              *
//              * Example:
//              *
//              * 01-Jan: repayment 20
//              * 01-Apr: coupon
//              *
//              * Coupon principal = 80
//              * --------------------------------------------------------
//              */
//             while (repaymentIndex < repaymentDates.size()
//                     && repaymentDates.get(repaymentIndex)
//                             .isBefore(paymentDate)) {

//                 LocalDate repaymentDate = repaymentDates.get(repaymentIndex);

//                 BigDecimal repaymentAmount = repaymentsByDate.get(repaymentDate);

//                 outstandingPrincipal = outstandingPrincipal.subtract(repaymentAmount);

//                 if (outstandingPrincipal.signum() < 0) {
//                     outstandingPrincipal = ZERO;
//                 }

//                 repaymentIndex++;
//             }

//             /*
//              * --------------------------------------------------------
//              * COUPON CALCULATION
//              * --------------------------------------------------------
//              *
//              * IMPORTANT:
//              *
//              * Coupon is ALWAYS based on the bond coupon rate.
//              *
//              * coupon =
//              *
//              * outstanding principal
//              * × coupon rate
//              * ÷ 100
//              * ÷ frequency
//              *
//              * The amortization percentage is NOT used here.
//              * --------------------------------------------------------
//              */
//             BigDecimal couponAmount = outstandingPrincipal
//                     .multiply(couponRate)
//                     .divide(
//                             HUNDRED.multiply(frequencyDivisor),
//                             CALCULATION_SCALE,
//                             RoundingMode.HALF_UP);

//             /*
//              * --------------------------------------------------------
//              * DO NOT REDUCE PRINCIPAL BEFORE COUPON ON SAME DATE
//              * --------------------------------------------------------
//              *
//              * If principal repayment and coupon occur on the same date,
//              * the coupon normally represents the coupon for the period
//              * ending on that date.
//              *
//              * Therefore:
//              *
//              * 100 principal
//              * 10% coupon
//              * 20 principal repayment
//              *
//              * On that date:
//              *
//              * coupon = 100 × 10% / frequency
//              * repayment = 20
//              *
//              * After payment:
//              *
//              * remaining = 80
//              * --------------------------------------------------------
//              */
//             payments.add(
//                     new CouponPayment(
//                             paymentDate,
//                             couponAmount,
//                             outstandingPrincipal));

//             /*
//              * --------------------------------------------------------
//              * APPLY PRINCIPAL REPAYMENT ON THE COUPON DATE
//              * --------------------------------------------------------
//              *
//              * The repayment affects all subsequent coupon periods.
//              * --------------------------------------------------------
//              */
//             while (repaymentIndex < repaymentDates.size()
//                     && repaymentDates.get(repaymentIndex)
//                             .equals(paymentDate)) {

//                 BigDecimal repaymentAmount = repaymentsByDate.get(paymentDate);

//                 outstandingPrincipal = outstandingPrincipal.subtract(repaymentAmount);

//                 if (outstandingPrincipal.signum() < 0) {
//                     outstandingPrincipal = ZERO;
//                 }

//                 repaymentIndex++;
//             }

//             /*
//              * --------------------------------------------------------
//              * STOP IF FULLY REPAID
//              * --------------------------------------------------------
//              */
//             if (outstandingPrincipal.signum() == 0) {
//                 /*
//                  * We can stop because there cannot be any further
//                  * coupon on zero principal.
//                  */
//                 break;
//             }
//         }

//         return payments;
//     }

//     /*
//      * ============================================================
//      * BUILD REPAYMENT MAP
//      * ============================================================
//      */
//     private Map<LocalDate, BigDecimal> repaymentsByDate(
//             List<PrincipalRepayment> repayments) {

//         Map<LocalDate, BigDecimal> repaymentsByDate = new LinkedHashMap<>();

//         for (PrincipalRepayment repayment : repayments) {

//             if (repayment == null) {
//                 throw new IllegalArgumentException(
//                         "Principal repayment cannot be null");
//             }

//             if (repayment.date() == null) {
//                 throw new IllegalArgumentException(
//                         "Principal repayment date cannot be null");
//             }

//             if (repayment.principalAmount() == null) {
//                 throw new IllegalArgumentException(
//                         "Principal repayment amount cannot be null");
//             }

//             if (repayment.remainingPrincipal() == null) {
//                 throw new IllegalArgumentException(
//                         "Remaining principal cannot be null");
//             }

//             if (repayment.principalAmount().signum() < 0) {
//                 throw new IllegalArgumentException(
//                         "Principal repayment cannot be negative");
//             }

//             repaymentsByDate.merge(
//                     repayment.date(),
//                     repayment.principalAmount(),
//                     BigDecimal::add);
//         }

//         return repaymentsByDate;
//     }

//     /*
//      * ============================================================
//      * INITIAL OUTSTANDING PRINCIPAL
//      * ============================================================
//      *
//      * PrincipalRepaymentService returns future repayments.
//      *
//      * Example:
//      *
//      * First future repayment:
//      *
//      * date = 01-01-2027
//      * repaymentAmount = 20
//      * remaining = 80
//      *
//      * Therefore principal before the first future repayment:
//      *
//      * 80 + 20 = 100
//      *
//      * Another example:
//      *
//      * Calculation date is after the first amortization:
//      *
//      * first future repayment:
//      *
//      * repaymentAmount = 20
//      * remaining = 60
//      *
//      * Therefore current outstanding principal:
//      *
//      * 60 + 20 = 80
//      *
//      * This is exactly what we need for future coupon calculation.
//      * ============================================================
//      */
//     private BigDecimal initialOutstandingPrincipal(
//             List<PrincipalRepayment> repayments) {

//         if (repayments == null || repayments.isEmpty()) {
//             return FACE_VALUE;
//         }

//         PrincipalRepayment firstRepayment = repayments.stream()
//                 .filter(repayment -> repayment != null)
//                 .min(
//                         Comparator.comparing(
//                                 PrincipalRepayment::date))
//                 .orElseThrow(
//                         () -> new IllegalArgumentException(
//                                 "Invalid principal repayment list"));

//         BigDecimal currentOutstanding = firstRepayment.remainingPrincipal()
//                 .add(firstRepayment.principalAmount());

//         if (currentOutstanding.signum() < 0) {
//             throw new IllegalArgumentException(
//                     "Outstanding principal cannot be negative");
//         }

//         return currentOutstanding;
//     }

//     /*
//      * ============================================================
//      * FREQUENCY DIVISOR
//      * ============================================================
//      */
//     private BigDecimal frequencyDivisor(
//             CouponFrequency frequency) {

//         if (frequency == null) {
//             throw new IllegalArgumentException(
//                     "Coupon frequency is required");
//         }

//         return switch (frequency) {

//             case MONTHLY ->
//                 BigDecimal.valueOf(12);

//             case QUARTERLY ->
//                 BigDecimal.valueOf(4);

//             case HALF_YEARLY ->
//                 BigDecimal.valueOf(2);

//             case YEARLY ->
//                 BigDecimal.ONE;

//             case AT_MATURITY ->
//                 throw new IllegalArgumentException(
//                         "Unsupported coupon frequency: AT_MATURITY");
//         };
//     }

//     /*
//      * ============================================================
//      * VALIDATION
//      * ============================================================
//      */
//     private void validateInput(
//             Bond bond,
//             List<LocalDate> couponDates,
//             List<PrincipalRepayment> principalRepayments,
//             LocalDate calculationDate) {

//         if (bond == null) {
//             throw new IllegalArgumentException(
//                     "Bond cannot be null");
//         }

//         if (couponDates == null) {
//             throw new IllegalArgumentException(
//                     "Coupon dates cannot be null");
//         }

//         if (principalRepayments == null) {
//             throw new IllegalArgumentException(
//                     "Principal repayments cannot be null");
//         }

//         if (calculationDate == null) {
//             throw new IllegalArgumentException(
//                     "Calculation date cannot be null");
//         }

//         if (couponDates.stream().anyMatch(date -> date == null)) {
//             throw new IllegalArgumentException(
//                     "Coupon dates cannot contain null");
//         }
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class CouponCalculationServiceImpl implements CouponCalculationService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final int CALCULATION_SCALE = 10;

    @Override
    public List<CouponPayment> calculateCoupons(
            Bond bond,
            List<LocalDate> couponDates,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {

        validateInput(
                bond,
                couponDates,
                principalRepayments,
                calculationDate);

        BigDecimal couponRate = bond.getCouponRate();

        if (couponRate == null) {
            throw new IllegalArgumentException(
                    "Coupon rate is required");
        }

        if (couponRate.signum() < 0) {
            throw new IllegalArgumentException(
                    "Coupon rate cannot be negative");
        }

        CouponFrequency couponFrequency = bond.getCouponFrequency();

        BigDecimal frequencyDivisor = frequencyDivisor(couponFrequency);

        /*
         * ------------------------------------------------------------
         * PRINCIPAL REPAYMENTS
         * ------------------------------------------------------------
         *
         * Convert repayments into:
         *
         * repayment date -> principal repaid
         *
         * Example:
         *
         * 2026-10-01 -> 20
         * 2027-01-01 -> 20
         * 2027-04-01 -> 20
         *
         * ------------------------------------------------------------
         */
        Map<LocalDate, BigDecimal> repaymentsByDate = repaymentsByDate(principalRepayments);

        /*
         * ------------------------------------------------------------
         * INITIAL OUTSTANDING PRINCIPAL
         * ------------------------------------------------------------
         *
         * The first repayment tells us:
         *
         * opening principal = remaining principal
         * + current repayment
         *
         * Example:
         *
         * remaining = 80
         * repayment = 20
         *
         * opening = 100
         *
         * For a bullet bond, it is simply 100.
         * ------------------------------------------------------------
         */
        BigDecimal outstandingPrincipal = initialOutstandingPrincipal(
                principalRepayments);

        /*
         * ------------------------------------------------------------
         * FUTURE COUPON DATES
         * ------------------------------------------------------------
         */
        List<LocalDate> paymentDates = couponDates.stream()
                .filter(date -> date != null)
                .filter(date -> date.isAfter(calculationDate))
                .filter(date -> bond.getMaturityDate() == null
                        || !date.isAfter(bond.getMaturityDate()))
                .distinct()
                .sorted()
                .toList();

        List<CouponPayment> payments = new ArrayList<>();

        /*
         * Sorted repayment dates.
         */
        List<LocalDate> repaymentDates = repaymentsByDate.keySet()
                .stream()
                .sorted()
                .toList();

        int repaymentIndex = 0;

        /*
         * ------------------------------------------------------------
         * COUPON CALCULATION
         * ------------------------------------------------------------
         *
         * IMPORTANT BUSINESS RULE:
         *
         * Coupon is always calculated using:
         *
         * bond.getCouponRate()
         *
         * The amortization percentage is NOT a coupon rate.
         *
         * The amortization percentage only determines how much
         * principal is repaid.
         *
         * Therefore:
         *
         * Before amortization:
         *
         * coupon = 100 × couponRate / frequency
         *
         * After amortization:
         *
         * coupon = outstandingPrincipal
         * × couponRate
         * / frequency
         *
         * ------------------------------------------------------------
         */
        for (LocalDate paymentDate : paymentDates) {

            /*
             * --------------------------------------------------------
             * APPLY ANY REPAYMENTS BEFORE THIS COUPON DATE
             * --------------------------------------------------------
             *
             * This handles a repayment that happened on an earlier
             * date than the current coupon date.
             *
             * Example:
             *
             * repayment = 2026-10-01
             * coupon = 2026-11-01
             *
             * November coupon must use the reduced principal.
             * --------------------------------------------------------
             */
            while (repaymentIndex < repaymentDates.size()
                    && repaymentDates
                            .get(repaymentIndex)
                            .isBefore(paymentDate)) {

                LocalDate repaymentDate = repaymentDates.get(repaymentIndex);

                BigDecimal repaymentAmount = repaymentsByDate.get(repaymentDate);

                outstandingPrincipal = outstandingPrincipal
                        .subtract(repaymentAmount);

                if (outstandingPrincipal.signum() < 0) {
                    outstandingPrincipal = ZERO;
                }

                repaymentIndex++;
            }

            /*
             * --------------------------------------------------------
             * COUPON ON OPENING PRINCIPAL
             * --------------------------------------------------------
             *
             * If principal repayment occurs on the same date as the
             * coupon, coupon is calculated BEFORE that day's principal
             * repayment.
             *
             * Example:
             *
             * Opening principal = 100
             * Coupon rate = 14.40%
             * Monthly
             *
             * Coupon = 100 × 14.40% / 12
             * = 1.20
             *
             * Then principal repayment of 20 occurs.
             *
             * Closing principal = 80
             * --------------------------------------------------------
             */
            BigDecimal couponAmount = calculateCoupon(
                    outstandingPrincipal,
                    couponRate,
                    frequencyDivisor);

            /*
             * Store coupon payment using the OPENING principal.
             */
            payments.add(
                    new CouponPayment(
                            paymentDate,
                            couponAmount,
                            outstandingPrincipal));

            /*
             * --------------------------------------------------------
             * APPLY REPAYMENT ON SAME DATE
             * --------------------------------------------------------
             *
             * Principal repayment happens AFTER coupon calculation.
             * --------------------------------------------------------
             */
            if (repaymentIndex < repaymentDates.size()
                    && repaymentDates
                            .get(repaymentIndex)
                            .equals(paymentDate)) {

                BigDecimal repaymentAmount = repaymentsByDate.get(paymentDate);

                outstandingPrincipal = outstandingPrincipal
                        .subtract(repaymentAmount);

                if (outstandingPrincipal.signum() < 0) {
                    outstandingPrincipal = ZERO;
                }

                repaymentIndex++;
            }

            /*
             * Once the principal has become zero, there is no reason
             * to generate additional coupon payments.
             */
            if (outstandingPrincipal.signum() == 0) {

                /*
                 * We still allow the current coupon payment to exist,
                 * because the coupon for this date was already earned
                 * on the opening principal.
                 */
                break;
            }
        }

        return payments;
    }

    /*
     * ============================================================
     * CALCULATE COUPON
     * ============================================================
     *
     * Coupon rate is ALWAYS the bond coupon rate.
     *
     * Amortization percentage does NOT replace coupon rate.
     *
     * Formula:
     *
     * principal × couponRate
     * ----------------------
     * 100 × frequency
     *
     * Example:
     *
     * principal = 80
     * couponRate = 14.40
     * frequency = monthly
     *
     * 80 × 14.40 / (100 × 12)
     *
     * = 0.96
     * ============================================================
     */
    private BigDecimal calculateCoupon(
            BigDecimal outstandingPrincipal,
            BigDecimal couponRate,
            BigDecimal frequencyDivisor) {

        if (outstandingPrincipal == null) {
            throw new IllegalArgumentException(
                    "Outstanding principal cannot be null");
        }

        if (outstandingPrincipal.signum() < 0) {
            throw new IllegalArgumentException(
                    "Outstanding principal cannot be negative");
        }

        return outstandingPrincipal
                .multiply(couponRate)
                .divide(
                        HUNDRED.multiply(frequencyDivisor),
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP);
    }

    /*
     * ============================================================
     * REPAYMENTS BY DATE
     * ============================================================
     */
    private Map<LocalDate, BigDecimal> repaymentsByDate(
            List<PrincipalRepayment> repayments) {

        Map<LocalDate, BigDecimal> repaymentsByDate = new LinkedHashMap<>();

        for (PrincipalRepayment repayment : repayments) {

            if (repayment == null
                    || repayment.date() == null
                    || repayment.principalAmount() == null
                    || repayment.remainingPrincipal() == null) {

                throw new IllegalArgumentException(
                        "Principal repayments must contain complete values");
            }

            if (repayment.principalAmount().signum() < 0) {
                throw new IllegalArgumentException(
                        "Principal repayment cannot be negative");
            }

            repaymentsByDate.merge(
                    repayment.date(),
                    repayment.principalAmount(),
                    BigDecimal::add);
        }

        return repaymentsByDate;
    }

    /*
     * ============================================================
     * INITIAL OUTSTANDING PRINCIPAL
     * ============================================================
     */
    private BigDecimal initialOutstandingPrincipal(
            List<PrincipalRepayment> repayments) {

        if (repayments == null
                || repayments.isEmpty()) {

            return FACE_VALUE;
        }

        PrincipalRepayment firstRepayment = repayments.stream()
                .filter(r -> r != null)
                .min(
                        Comparator.comparing(
                                PrincipalRepayment::date))
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Unable to determine initial principal"));

        /*
         * Example:
         *
         * repayment = 20
         * remaining = 80
         *
         * initial = 20 + 80 = 100
         */
        BigDecimal initialPrincipal = firstRepayment
                .remainingPrincipal()
                .add(firstRepayment.principalAmount());

        if (initialPrincipal.compareTo(FACE_VALUE) > 0) {
            throw new IllegalArgumentException(
                    "Initial outstanding principal cannot exceed face value");
        }

        return initialPrincipal;
    }

    /*
     * ============================================================
     * FREQUENCY DIVISOR
     * ============================================================
     */
    private BigDecimal frequencyDivisor(
            CouponFrequency frequency) {

        if (frequency == null) {
            throw new IllegalArgumentException(
                    "Coupon frequency is required");
        }

        return switch (frequency) {

            case MONTHLY ->
                BigDecimal.valueOf(12);

            case QUARTERLY ->
                BigDecimal.valueOf(4);

            case HALF_YEARLY ->
                BigDecimal.valueOf(2);

            case YEARLY ->
                BigDecimal.ONE;

            case AT_MATURITY ->
                throw new IllegalArgumentException(
                        "Unsupported coupon frequency: AT_MATURITY");
        };
    }

    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */
    private void validateInput(
            Bond bond,
            List<LocalDate> couponDates,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {

        if (bond == null) {
            throw new IllegalArgumentException(
                    "Bond cannot be null");
        }

        if (couponDates == null) {
            throw new IllegalArgumentException(
                    "Coupon dates cannot be null");
        }

        if (principalRepayments == null) {
            throw new IllegalArgumentException(
                    "Principal repayments cannot be null");
        }

        if (calculationDate == null) {
            throw new IllegalArgumentException(
                    "Calculation date cannot be null");
        }

        if (couponDates.stream()
                .anyMatch(date -> date == null)) {

            throw new IllegalArgumentException(
                    "Coupon dates cannot contain null");
        }
    }

}