package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class CouponCalculationServiceImpl implements CouponCalculationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /*
     * Day count for a broken (stub) coupon period. A stub covers part of a
     * period, so it cannot be priced by dividing an annual coupon by the
     * frequency; it is accrued over the actual days of the period instead.
     */
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

    private static final int CALCULATION_SCALE = 10;

    private final CouponDateGenerator couponDateGenerator;

    @Autowired
    public CouponCalculationServiceImpl(
            CouponDateGenerator couponDateGenerator) {

        this.couponDateGenerator = Objects.requireNonNull(
                couponDateGenerator,
                "CouponDateGenerator is required");
    }

    /**
     * Convenience constructor for callers that hold no generator. The stub
     * coupon logic is the only part of this service that needs one, and a
     * generator is dependency-free, so no Spring context is required.
     */
    public CouponCalculationServiceImpl() {

        this(new CouponDateGenerator());
    }

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
         * One unit's face value, with the bond's lot applied. The repayments
         * arriving from PrincipalRepaymentService are already on this base, and
         * so is every coupon computed below; the opening principal read from
         * them therefore is too.
         */
        BigDecimal faceValue = BondFaceValue.of(bond);

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
                principalRepayments,
                faceValue);

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
         * One exception, applied below: a monthly coupon of a bond that is
         * not a Sovereign is earned over the actual days of its period
         * instead of a flat twelfth of the year. Every other bond, a
         * Sovereign included, keeps the formula above.
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
            BigDecimal couponAmount;

            if (MonthlyCouponDayCount.appliesTo(bond)) {

                /*
                 * A monthly non-Sovereign bond is priced over the days its
                 * period actually covers, so this covers the stub period too:
                 * it is opened by the same previous anniversary as any other
                 * period, and is measured over the year it is paid in.
                 */
                couponAmount = calculateMonthlyCoupon(
                        bond,
                        outstandingPrincipal,
                        couponRate,
                        paymentDate);

            } else if (couponDateGenerator.isStubCouponDate(
                    bond,
                    paymentDate)) {

                couponAmount = calculateStubCoupon(
                        bond,
                        outstandingPrincipal,
                        couponRate,
                        paymentDate);

            } else {

                couponAmount = calculateCoupon(
                        outstandingPrincipal,
                        couponRate,
                        frequencyDivisor);
            }

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
     * CALCULATE MONTHLY COUPON
     * ============================================================
     *
     * A monthly coupon of a bond that is not a Sovereign is earned over the
     * days its period actually covers, not over a flat twelfth of a year.
     *
     * Formula (Actual/365, or Actual/366 for a coupon paid in a leap year):
     *
     * principal × couponRate × days in the period
     * -------------------------------------------
     *          100 × days in the year
     *
     * Example:
     *
     * principal = 100
     * couponRate = 12.00
     * 01-Jan-2027 -> 01-Feb-2027 = 31 days, paid in a 365-day year
     *
     * 100 × 12.00 × 31 / (100 × 365) = 1.0191780822
     *
     * Example, a coupon paid in a leap year:
     *
     * principal = 100
     * couponRate = 12.00
     * 01-Feb-2028 -> 01-Mar-2028 = 29 days, paid in a 366-day year
     *
     * 100 × 12.00 × 29 / (100 × 366) = 0.9508196721
     *
     * The year is taken from the payment date, so a period spanning the end of
     * a year is not split: 17-Dec-2028 -> 17-Jan-2029 is 31 days paid in 2029,
     * and is measured over 365.
     *
     * The period opens on the previous anniversary, which is also what opens
     * the stub period - see
     * {@link CouponDateGenerator#previousScheduledDate(Bond, LocalDate)}.
     * ============================================================
     */
    private BigDecimal calculateMonthlyCoupon(
            Bond bond,
            BigDecimal outstandingPrincipal,
            BigDecimal couponRate,
            LocalDate paymentDate) {

        LocalDate periodStart = monthlyPeriodStart(bond, paymentDate);

        if (periodStart == null) {

            throw new IllegalArgumentException(
                    "Unable to determine the period opening the monthly coupon on "
                            + paymentDate);
        }

        return MonthlyCouponDayCount.coupon(
                outstandingPrincipal,
                couponRate,
                periodStart,
                paymentDate,
                CALCULATION_SCALE);
    }

    /**
     * The date the monthly period ending on {@code paymentDate} opened on.
     *
     * <p>Read from the bond's own anniversary description, so a bond paying on
     * the 31st opens November's period on 31 October and December's on
     * 30 November - the dates the schedule itself pays on, rather than a month
     * subtracted from a date the previous month had already clamped.
     *
     * <p>A bond with no anniversary description to read has no schedule to
     * consult, so its period opens on the same day of the previous month.
     * Callers reaching this service through
     * {@link BondCashFlowServiceImpl} always have a description.
     */
    private LocalDate monthlyPeriodStart(
            Bond bond,
            LocalDate paymentDate) {

        LocalDate scheduled = couponDateGenerator.previousScheduledDate(
                bond,
                paymentDate);

        return scheduled != null
                ? scheduled
                : paymentDate.minusMonths(1);
    }

    /*
     * ============================================================
     * CALCULATE STUB COUPON
     * ============================================================
     *
     * The final coupon of a bond whose maturity falls short of the next
     * anniversary covers only the days between the previous coupon date and
     * maturity.
     *
     * Formula (Actual/365):
     *
     * principal × couponRate × days
     * ------------------------------
     *          100 × 365
     *
     * Example:
     *
     * principal = 100
     * couponRate = 9.10
     * 20-Nov-2035 -> 18-Jan-2036 = 59 days
     *
     * 100 × 9.10 × 59 / (100 × 365) = 1.4709589041
     *
     * The amount uses the same opening principal as a regular coupon, so a
     * repayment landing on the stub date is still applied afterwards.
     * ============================================================
     */
    private BigDecimal calculateStubCoupon(
            Bond bond,
            BigDecimal outstandingPrincipal,
            BigDecimal couponRate,
            LocalDate paymentDate) {

        LocalDate periodStart = couponDateGenerator.stubPeriodStart(
                bond,
                paymentDate);

        if (periodStart == null) {

            throw new IllegalArgumentException(
                    "Unable to determine the period opening the stub coupon on "
                            + paymentDate);
        }

        long days = ChronoUnit.DAYS.between(periodStart, paymentDate);

        if (days <= 0) {

            throw new IllegalArgumentException(
                    "Stub coupon period must cover at least one day: "
                            + periodStart + " to " + paymentDate);
        }

        return outstandingPrincipal
                .multiply(couponRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(
                        HUNDRED.multiply(DAYS_PER_YEAR),
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
            List<PrincipalRepayment> repayments,
            BigDecimal faceValue) {

        if (repayments == null
                || repayments.isEmpty()) {

            return faceValue;
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

        if (initialPrincipal.compareTo(faceValue) > 0) {
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