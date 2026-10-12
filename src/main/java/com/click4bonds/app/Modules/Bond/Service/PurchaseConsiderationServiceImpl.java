package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PurchaseConsideration;
import com.click4bonds.app.Modules.Bond.Enums.PurchasePriceTreatment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link PurchaseConsiderationService}.
 *
 * <h2>Convention implemented here</h2>
 *
 * <pre>
 * CUM_INTEREST : purchase = clean price + accrued interest
 * EX_INTEREST  : purchase = clean price
 * </pre>
 *
 * <p>
 * A purchase is ex-interest when the buyer is not entitled to the first future
 * coupon. The projected accrued interest reported by
 * {@code AccruedInterestService} is a fraction of exactly that coupon, so it is
 * the seller's compensation for keeping it. Charging it to a buyer who will not
 * receive the coupon would pay the seller twice, once through the coupon and
 * again through the price, which is why it is dropped rather than merely
 * removed from the future series.
 *
 * <h2>Worked example: 12% Satin Creditcare 2031, "15 days prior"</h2>
 *
 * <pre>
 * calculationDate 2026-10-09
 *   first future coupon     2026-10-23
 *   its record date         2026-10-08
 *   2026-10-09 &gt; 2026-10-08  -&gt; not entitled -&gt; EX_INTEREST
 *   clean price 98.94, accrued interest 0.51613 (16/31 of 1.00)
 *   purchase = 98.94        (accrued interest NOT charged)
 * </pre>
 *
 * <h2>Absence of a record-date rule</h2>
 *
 * <p>
 * When the bond has no record-date description the parser resolves no record
 * date, entitlement falls back to "is the coupon in the future", and the
 * treatment is CUM_INTEREST with accrued interest charged. No bond is ever
 * classified as ex-interest without a record-date rule, so historical YTM is
 * unchanged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseConsiderationServiceImpl implements PurchaseConsiderationService {

    private final RecordDateParser recordDateParser;
    private final CouponEntitlementService couponEntitlementService;

    @Override
    public PurchaseConsideration determine(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal accruedInterest,
            List<CouponPayment> couponPayments) {

        validateInput(bond, calculationDate, accruedInterest);

        BigDecimal cleanPrice = BondFaceValue.cleanPrice(bond);
        Treatment treatment = resolveTreatment(bond, calculationDate, couponPayments);
        BigDecimal accruedInterestCharged = accruedInterestCharged(treatment, accruedInterest);

        if (treatment.treatment() == PurchasePriceTreatment.EX_INTEREST) {

            log.debug(
                    "Ex-interest purchase: isin={} calculationDate={} upcomingPaymentDate={} upcomingRecordDate={} cleanPrice={} accruedInterestNotCharged={}",
                    bond.getIsin(),
                    calculationDate,
                    treatment.upcomingPaymentDate(),
                    treatment.upcomingRecordDate(),
                    cleanPrice,
                    accruedInterest);
        }

        return new PurchaseConsideration(
                treatment.treatment(),
                cleanPrice,
                accruedInterestCharged,
                cleanPrice.add(accruedInterestCharged),
                treatment.upcomingPaymentDate(),
                treatment.upcomingRecordDate());
    }

    @Override
    public BigDecimal accruedInterestCharged(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal accruedInterest,
            List<CouponPayment> couponPayments) {

        validateInput(bond, calculationDate, accruedInterest);

        return accruedInterestCharged(
                resolveTreatment(bond, calculationDate, couponPayments),
                accruedInterest);
    }

    /**
     * The treatment decided by the first future coupon and that coupon's own
     * record date, together with the dates it was decided from.
     *
     * <p>
     * Kept apart from the money so the same decision serves both
     * {@link #determine} — which needs the dates to report them — and
     * {@link #accruedInterestCharged}, which needs only the treatment.
     */
    private Treatment resolveTreatment(
            Bond bond,
            LocalDate calculationDate,
            List<CouponPayment> couponPayments) {

        Optional<CouponPayment> upcomingCoupon = firstFutureCoupon(
                couponPayments,
                calculationDate);

        /*
         * Nothing left to project. There is no upcoming coupon whose record date
         * could make this purchase ex-interest, so keep the historical
         * consideration rather than inventing a treatment.
         */
        if (upcomingCoupon.isEmpty()) {

            return new Treatment(
                    PurchasePriceTreatment.CUM_INTEREST,
                    null,
                    null);
        }

        LocalDate paymentDate = upcomingCoupon.get().date();

        /*
         * The record date is resolved for THIS coupon only. The next coupon in
         * the schedule has its own record date and is treated independently.
         */
        LocalDate recordDate = recordDateParser
                .calculateRecordDate(bond, paymentDate)
                .orElse(null);

        boolean entitled = couponEntitlementService.isCouponEntitled(
                calculationDate,
                recordDate,
                paymentDate);

        return new Treatment(
                entitled
                        ? PurchasePriceTreatment.CUM_INTEREST
                        : PurchasePriceTreatment.EX_INTEREST,
                paymentDate,
                recordDate);
    }

    /**
     * Charging the accrual under cum-interest and nothing under ex-interest: the
     * accrual is the seller's compensation for the coupon the buyer will not
     * receive, so charging it as well would pay the seller twice.
     */
    private static BigDecimal accruedInterestCharged(
            Treatment treatment,
            BigDecimal accruedInterest) {

        return treatment.treatment() == PurchasePriceTreatment.EX_INTEREST
                ? BigDecimal.ZERO
                : accruedInterest;
    }

    /**
     * @return the earliest projected coupon falling after the calculation date
     */
    private Optional<CouponPayment> firstFutureCoupon(
            List<CouponPayment> couponPayments,
            LocalDate calculationDate) {

        if (couponPayments == null) {
            return Optional.empty();
        }

        return couponPayments.stream()
                .filter(payment -> payment != null && payment.date() != null)
                .filter(payment -> payment.date().isAfter(calculationDate))
                .min(Comparator.comparing(CouponPayment::date));
    }

    private void validateInput(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal accruedInterest) {

        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }

        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }

        if (accruedInterest == null) {
            throw new IllegalArgumentException("Accrued interest cannot be null");
        }
    }

    /**
     * The settlement convention and the coupon it was read from.
     *
     * @param treatment          cum- or ex-interest
     * @param upcomingPaymentDate the first future coupon's payment date, or
     *                            {@code null} when the bond has no future coupon
     * @param upcomingRecordDate  that coupon's record date, or {@code null} when
     *                            none was resolved
     */
    private record Treatment(
            PurchasePriceTreatment treatment,
            LocalDate upcomingPaymentDate,
            LocalDate upcomingRecordDate) {
    }
}
