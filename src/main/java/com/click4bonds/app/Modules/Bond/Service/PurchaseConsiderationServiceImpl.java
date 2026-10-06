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

        BigDecimal cleanPrice = bond.getPrice();
        BigDecimal cumInterestConsideration = cleanPrice.add(accruedInterest);

        Optional<CouponPayment> upcomingCoupon = firstFutureCoupon(
                couponPayments,
                calculationDate);

        /*
         * Nothing left to project. There is no upcoming coupon whose record date
         * could make this purchase ex-interest, so keep the historical
         * consideration rather than inventing a treatment.
         */
        if (upcomingCoupon.isEmpty()) {

            return new PurchaseConsideration(
                    PurchasePriceTreatment.CUM_INTEREST,
                    cleanPrice,
                    accruedInterest,
                    cumInterestConsideration,
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

        if (entitled) {

            return new PurchaseConsideration(
                    PurchasePriceTreatment.CUM_INTEREST,
                    cleanPrice,
                    accruedInterest,
                    cumInterestConsideration,
                    paymentDate,
                    recordDate);
        }

        log.debug(
                "Ex-interest purchase: isin={} calculationDate={} upcomingPaymentDate={} upcomingRecordDate={} cleanPrice={} accruedInterestNotCharged={}",
                bond.getIsin(),
                calculationDate,
                paymentDate,
                recordDate,
                cleanPrice,
                accruedInterest);

        return new PurchaseConsideration(
                PurchasePriceTreatment.EX_INTEREST,
                cleanPrice,
                BigDecimal.ZERO,
                cleanPrice,
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
}
