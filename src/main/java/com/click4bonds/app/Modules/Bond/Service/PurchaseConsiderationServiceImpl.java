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
 * CUM_INTEREST : accrued interest positive -&gt; purchase = clean price + accrual
 * EX_INTEREST  : accrued interest negative -&gt; purchase = clean price + accrual
 *                                             = clean price - |accrual|
 * </pre>
 *
 * <p>
 * The treatment is one and the same thing as the sign of the accrual, so this
 * service does not adjust the accrual - it charges the figure
 * {@code AccruedInterestService} produced. A purchase is ex-interest when the
 * buyer is not entitled to the first future coupon, and the accrual for such a
 * settlement is negative: it is the part of that coupon covering the stretch
 * the buyer will hold without being paid. Setting it against the price is what
 * keeps the clean price continuous as the bond goes ex.
 *
 * <h2>Worked example: 12% monthly bond paying on the 17th, record date 2nd</h2>
 *
 * <pre>
 * calculationDate 2026-05-12
 *   first future coupon     2026-05-17
 *   its record date         2026-05-02
 *   2026-05-12 &gt; 2026-05-02  -&gt; not entitled -&gt; EX_INTEREST
 *   clean price 98.94, accrual -5 days of the 30-day period, i.e. -0.16438
 *   purchase = 98.94 - 0.16438 = 98.77562
 * </pre>
 *
 * <h2>Absence of a record-date rule</h2>
 *
 * <p>
 * When the bond has no record-date offset the parser resolves no record date,
 * entitlement falls back to "is the coupon in the future", and the accrual is
 * the ordinary positive one. No bond is ever classified as ex-interest without a
 * record-date rule, so historical YTM is unchanged.
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

        /*
         * The accrual is charged exactly as it stands, sign included: positive
         * on a cum-interest purchase (interest run up to settlement), negative
         * on an ex-interest one (the part of the coming coupon the buyer will
         * not receive, set against the price). Nothing is zeroed here, because
         * the sign is already the treatment - dropping the negative accrual
         * would leave the figure AccruedInterestService reports with nothing
         * behind it, and would charge the buyer for a coupon the seller keeps.
         */
        BigDecimal accruedInterestCharged = accruedInterest;

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

        /*
         * The same value the consideration is built from, so the price and the
         * figure reported beside it can never disagree. The treatment is not
         * consulted: it is the accrual's sign, resolved on the same coupon.
         */
        return accruedInterest;
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
