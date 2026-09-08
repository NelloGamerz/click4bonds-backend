// package com.click4bonds.app.Modules.Bond.Service;

// import java.math.BigDecimal;
// import java.math.RoundingMode;
// import java.time.LocalDate;
// import java.time.temporal.ChronoUnit;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Objects;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
// import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
// import com.click4bonds.app.Modules.Bond.Models.Bond;

// @Service
// public class AccruedInterestServiceImpl implements AccruedInterestService {

//     private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
//     private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
//     private static final BigDecimal ACCRUAL_DAYS = BigDecimal.valueOf(365);
//     private static final int CALCULATION_SCALE = 20;

//     private final CouponScheduleService couponScheduleService;
//     private final MaturityDescriptionParser maturityDescriptionParser;
//     private final CouponDateGenerator couponDateGenerator;
//     private final PrincipalRepaymentService principalRepaymentService;

//     /**
//      * Full constructor used by the Spring container. Supplies all of the
//      * dependencies required to determine the outstanding principal at the
//      * settlement date for amortizing bonds.
//      */
//     @Autowired
//     public AccruedInterestServiceImpl(
//             CouponScheduleService couponScheduleService,
//             MaturityDescriptionParser maturityDescriptionParser,
//             CouponDateGenerator couponDateGenerator,
//             PrincipalRepaymentService principalRepaymentService) {
//         this.couponScheduleService = couponScheduleService;
//         this.maturityDescriptionParser = maturityDescriptionParser;
//         this.couponDateGenerator = couponDateGenerator;
//         this.principalRepaymentService = principalRepaymentService;
//     }

//     /**
//      * Lightweight constructor retained for the existing unit tests, which
//      * only exercise non-amortizing (bullet) bonds. With no principal
//      * services wired in, the outstanding principal is assumed to be the
//      * full face value, which is exactly correct for a bullet bond.
//      */
//     public AccruedInterestServiceImpl(CouponScheduleService couponScheduleService) {
//         this(couponScheduleService, null, null, null);
//     }

//     @Override
//     public BigDecimal calculate(Bond bond, LocalDate calculationDate) {
//         validateInput(bond, calculationDate);

//         if (bond.getCouponRate().signum() == 0
//                 || bond.getCouponFrequency() == CouponFrequency.AT_MATURITY) {
//             return BigDecimal.ZERO;
//         }

//         CouponScheduleService.CouponSchedule schedule = couponScheduleService.resolve(bond, calculationDate);
//         if (schedule.previous() == null || schedule.next() == null
//                 || schedule.previous().equals(calculationDate)) {
//             return BigDecimal.ZERO;
//         }

//         BigDecimal frequencyDivisor = frequencyDivisor(bond.getCouponFrequency());

//         /*
//          * The coupon component of the accrued interest must be based on the
//          * principal that is actually outstanding at the settlement date,
//          * not on the original face value. If principal has already been
//          * amortized before settlement, that repaid amount must be reflected
//          * here.
//          */
//         BigDecimal outstandingPrincipal = outstandingPrincipalAtSettlement(bond, calculationDate);

//         BigDecimal couponAmount = outstandingPrincipal.multiply(bond.getCouponRate())
//                 .divide(HUNDRED.multiply(frequencyDivisor), CALCULATION_SCALE, RoundingMode.HALF_UP);
//         long accruedDays = ChronoUnit.DAYS.between(schedule.previous(), calculationDate);

//         return couponAmount.multiply(BigDecimal.valueOf(accruedDays))
//                 .divide(ACCRUAL_DAYS, CALCULATION_SCALE, RoundingMode.HALF_UP);
//     }

//     /**
//      * Determines the principal outstanding at the settlement date.
//      *
//      * <p>The source of truth is the principal repayment schedule produced by
//      * {@link PrincipalRepaymentService}. The full schedule is regenerated so
//      * that repayments which have already occurred before the settlement date
//      * are included, then every repayment strictly before the settlement date
//      * is subtracted from the face value.
//      *
//      * <p>Same-day convention: a principal repayment that falls exactly on the
//      * settlement date is applied <em>after</em> the coupon for that date (this
//      * mirrors the coupon/repayment ordering used by
//      * {@link CouponCalculationServiceImpl}). Such a repayment therefore does
//      * not reduce the principal that the accrual period earned on, and so it is
//      * not subtracted here. This behaviour is exercised by a dedicated test.
//      *
//      * @return the outstanding principal at the settlement date, or the full
//      *         face value for a non-amortizing bond.
//      */
//     private BigDecimal outstandingPrincipalAtSettlement(
//             Bond bond,
//             LocalDate settlement) {

//         if (principalRepaymentService == null) {
//             return FACE_VALUE;
//         }

//         MaturitySchedule schedule = maturityDescriptionParser.parse(
//                 bond.getMaturityDescription(), bond.getMaturityDate());

//         if (schedule.perpetual() || schedule.amortizationRules().isEmpty()) {
//             return FACE_VALUE;
//         }

//         LocalDate earliest = schedule.amortizationRules().stream()
//                 .map(AmortizationRule::startDate)
//                 .filter(Objects::nonNull)
//                 .min(LocalDate::compareTo)
//                 .orElse(bond.getMaturityDate());

//         LocalDate generationDate = earliest.minusDays(1);

//         List<LocalDate> couponDates = couponDates(bond, generationDate);

//         List<PrincipalRepayment> fullSchedule =
//                 principalRepaymentService.generateRepayments(
//                         bond,
//                         couponDates,
//                         generationDate);

//         BigDecimal repaidBeforeSettlement = fullSchedule.stream()
//                 .filter(repayment -> repayment.date().isBefore(settlement))
//                 .map(PrincipalRepayment::principalAmount)
//                 .reduce(BigDecimal.ZERO, BigDecimal::add);

//         BigDecimal outstanding = FACE_VALUE.subtract(repaidBeforeSettlement);

//         if (outstanding.signum() < 0) {
//             return BigDecimal.ZERO;
//         }
//         return outstanding;
//     }

//     private List<LocalDate> couponDates(Bond bond, LocalDate generationDate) {
//         if (bond.getIpDateDescription() == null
//                 || bond.getIpDateDescription().isBlank()) {
//             return new ArrayList<>();
//         }
//         List<LocalDate> dates = couponDateGenerator.generate(bond, generationDate);
//         return dates == null ? new ArrayList<>() : dates;
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

//     private void validateInput(Bond bond, LocalDate calculationDate) {
//         if (bond == null) {
//             throw new IllegalArgumentException("Bond cannot be null");
//         }
//         if (calculationDate == null) {
//             throw new IllegalArgumentException("Calculation date cannot be null");
//         }
//         if (bond.getCouponRate() == null) {
//             throw new IllegalArgumentException("Coupon rate is required");
//         }
//         if (bond.getCouponRate().signum() < 0) {
//             throw new IllegalArgumentException("Coupon rate cannot be negative");
//         }
//         if (bond.getMaturityDate() != null && bond.getMaturityDate().isBefore(calculationDate)) {
//             throw new IllegalArgumentException("Bond maturity date is before calculation date");
//         }
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class AccruedInterestServiceImpl implements AccruedInterestService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int CALCULATION_SCALE = 20;

    private final CouponScheduleService couponScheduleService;
    private final MaturityDescriptionParser maturityDescriptionParser;
    private final CouponDateGenerator couponDateGenerator;
    private final PrincipalRepaymentService principalRepaymentService;

    /**
     * Full constructor used by the Spring container.
     */
    @Autowired
    public AccruedInterestServiceImpl(
            CouponScheduleService couponScheduleService,
            MaturityDescriptionParser maturityDescriptionParser,
            CouponDateGenerator couponDateGenerator,
            PrincipalRepaymentService principalRepaymentService) {

        this.couponScheduleService = couponScheduleService;
        this.maturityDescriptionParser = maturityDescriptionParser;
        this.couponDateGenerator = couponDateGenerator;
        this.principalRepaymentService = principalRepaymentService;
    }

    /**
     * Lightweight constructor retained for existing unit tests that exercise
     * non-amortizing/bullet bonds.
     *
     * For bullet bonds, the outstanding principal is assumed to be the
     * full face value.
     */
    public AccruedInterestServiceImpl(
            CouponScheduleService couponScheduleService) {

        this(
                couponScheduleService,
                null,
                null,
                null);
    }

    @Override
    public BigDecimal calculate(
            Bond bond,
            LocalDate calculationDate) {

        validateInput(bond, calculationDate);

        /*
         * No accrued interest for:
         * - zero coupon bonds
         * - AT_MATURITY coupon frequency
         */
        if (bond.getCouponRate().signum() == 0
                || bond.getCouponFrequency() == CouponFrequency.AT_MATURITY) {

            return BigDecimal.ZERO;
        }

        /*
         * Resolve the coupon period containing the calculation date.
         *
         * Example:
         *
         * Previous coupon : 2026-09-05
         * Calculation date : 2026-09-08
         * Next coupon : 2026-10-05
         */
        CouponScheduleService.CouponSchedule schedule = couponScheduleService.resolve(
                bond,
                calculationDate);

        /*
         * No accrued interest when:
         * - previous coupon does not exist
         * - next coupon does not exist
         * - calculation date is exactly the coupon date
         */
        if (schedule.previous() == null
                || schedule.next() == null
                || schedule.previous().equals(calculationDate)) {

            return BigDecimal.ZERO;
        }

        /*
         * Determine the principal actually outstanding at settlement.
         *
         * This is important for amortizing bonds because coupon accrual
         * should only be calculated on the principal that is outstanding.
         */
        BigDecimal outstandingPrincipal = outstandingPrincipalAtSettlement(
                bond,
                calculationDate);

        /*
         * Calculate the coupon amount for the complete coupon period.
         *
         * Example for a 12.50% monthly coupon on face value 100:
         *
         * 100 × 12.50% / 12
         * = 1.0416666667
         */
        BigDecimal frequencyDivisor = frequencyDivisor(
                bond.getCouponFrequency());

        BigDecimal couponAmount = outstandingPrincipal
                .multiply(bond.getCouponRate())
                .divide(
                        HUNDRED.multiply(frequencyDivisor),
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP);

        /*
         * Calculate the number of days that have accrued.
         *
         * Example:
         *
         * Previous coupon : 2026-09-05
         * Calculation date : 2026-09-08
         *
         * Accrued days = 3
         */
        long accruedDays = ChronoUnit.DAYS.between(
                schedule.previous(),
                calculationDate);

        /*
         * Calculate the actual number of days in the coupon period.
         *
         * Example:
         *
         * 2026-09-05 -> 2026-10-05
         * = 30 days
         */
        long couponPeriodDays = ChronoUnit.DAYS.between(
                schedule.previous(),
                schedule.next());

        if (couponPeriodDays <= 0) {
            throw new IllegalStateException(
                    "Invalid coupon period: previous coupon date "
                            + schedule.previous()
                            + ", next coupon date "
                            + schedule.next());
        }

        /*
         * Accrued Interest:
         *
         * Coupon Amount × Accrued Days / Coupon Period Days
         *
         * For this bond:
         *
         * 1.0416666667 × 3 / 30
         * = 0.1041666667
         */
        return couponAmount
                .multiply(BigDecimal.valueOf(accruedDays))
                .divide(
                        BigDecimal.valueOf(couponPeriodDays),
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP);
    }

    /**
     * Determines the principal outstanding at the settlement date.
     *
     * The source of truth is the principal repayment schedule produced by
     * PrincipalRepaymentService.
     *
     * Repayments strictly before the settlement date are deducted from
     * the face value.
     *
     * A repayment occurring exactly on the settlement date is NOT deducted.
     * It is considered to occur after the coupon for that date, matching
     * the convention used by CouponCalculationServiceImpl.
     *
     * @param bond       the bond
     * @param settlement settlement/calculation date
     * @return outstanding principal
     */
    private BigDecimal outstandingPrincipalAtSettlement(
            Bond bond,
            LocalDate settlement) {

        /*
         * Lightweight constructor is used for bullet-bond tests.
         *
         * In that case there is no principal repayment service, so the
         * entire face value remains outstanding.
         */
        if (principalRepaymentService == null) {
            return FACE_VALUE;
        }

        MaturitySchedule schedule = maturityDescriptionParser.parse(
                bond.getMaturityDescription(),
                bond.getMaturityDate());

        /*
         * Perpetual or non-amortizing bonds retain the full face value.
         */
        if (schedule.perpetual()
                || schedule.amortizationRules().isEmpty()) {

            return FACE_VALUE;
        }

        /*
         * Find the earliest amortization rule.
         */
        LocalDate earliest = schedule.amortizationRules()
                .stream()
                .map(AmortizationRule::startDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(bond.getMaturityDate());

        /*
         * Generate the schedule from one day before the earliest
         * amortization date so that all historical repayments can
         * be included.
         */
        LocalDate generationDate = earliest.minusDays(1);

        List<LocalDate> couponDates = couponDates(
                bond,
                generationDate);

        List<PrincipalRepayment> fullSchedule = principalRepaymentService.generateRepayments(
                bond,
                couponDates,
                generationDate);

        /*
         * Sum all repayments that occurred strictly before settlement.
         */
        BigDecimal repaidBeforeSettlement = fullSchedule
                .stream()
                .filter(repayment -> repayment.date().isBefore(settlement))
                .map(PrincipalRepayment::principalAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);

        /*
         * Outstanding principal =
         * Face value - principal already repaid.
         */
        BigDecimal outstanding = FACE_VALUE.subtract(
                repaidBeforeSettlement);

        /*
         * Protect against negative outstanding principal caused by
         * overlapping or excessive repayment schedules.
         */
        if (outstanding.signum() < 0) {
            return BigDecimal.ZERO;
        }

        return outstanding;
    }

    /**
     * Generates coupon dates required for determining the principal
     * outstanding for amortizing bonds.
     */
    private List<LocalDate> couponDates(
            Bond bond,
            LocalDate generationDate) {

        if (bond.getIpDateDescription() == null
                || bond.getIpDateDescription().isBlank()) {

            return new ArrayList<>();
        }

        List<LocalDate> dates = couponDateGenerator.generate(
                bond,
                generationDate);

        return dates == null
                ? new ArrayList<>()
                : dates;
    }

    /**
     * Returns the number of coupon payments per year.
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

    /**
     * Validates calculation input.
     */
    private void validateInput(
            Bond bond,
            LocalDate calculationDate) {

        if (bond == null) {
            throw new IllegalArgumentException(
                    "Bond cannot be null");
        }

        if (calculationDate == null) {
            throw new IllegalArgumentException(
                    "Calculation date cannot be null");
        }

        if (bond.getCouponRate() == null) {
            throw new IllegalArgumentException(
                    "Coupon rate is required");
        }

        if (bond.getCouponRate().signum() < 0) {
            throw new IllegalArgumentException(
                    "Coupon rate cannot be negative");
        }

        if (bond.getMaturityDate() != null
                && bond.getMaturityDate().isBefore(calculationDate)) {

            throw new IllegalArgumentException(
                    "Bond maturity date is before calculation date");
        }
    }
}