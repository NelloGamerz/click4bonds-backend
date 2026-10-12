package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PurchaseConsideration;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Decides the purchase-price treatment for a YTM projection.
 *
 * <p>
 * This is the single place that knows the settlement convention, kept separate
 * from both {@link RecordDateParser} (when the record date is) and
 * {@link CouponEntitlementService} (whether the buyer receives the coupon), so
 * the convention can be changed without touching either of them or the XIRR
 * calculation.
 *
 * <p>
 * The treatment is decided by the <strong>first future coupon</strong> and that
 * coupon's <strong>own</strong> record date. It is never decided from a single
 * bond-level record-date flag, because a bond with recurring coupons moves in
 * and out of the ex-interest window once per coupon period.
 */
public interface PurchaseConsiderationService {

    /**
     * @param bond             the bond; must not be {@code null}
     * @param calculationDate  the purchase/settlement date; must not be
     *                         {@code null}
     * @param accruedInterest  accrued interest from
     *                         {@code AccruedInterestService}, charged exactly as
     *                         it stands — positive on a cum-interest purchase,
     *                         negative on an ex-interest one
     * @param couponPayments   the projected coupon payments; may be {@code null}
     * @return the purchase leg and the convention that produced it
     * @throws IllegalArgumentException when {@code bond},
     *                                  {@code calculationDate} or
     *                                  {@code accruedInterest} is {@code null}
     */
    PurchaseConsideration determine(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal accruedInterest,
            List<CouponPayment> couponPayments);

    /**
     * The accrued interest a purchase at {@code calculationDate} charges, on its
     * own.
     *
     * <p>
     * The same figure {@link #determine} builds its consideration from, and the
     * same one {@code AccruedInterestService} reports: the accrual, sign
     * included. It is reachable separately because pricing a bond from a yield
     * needs the accrual <em>without</em> a price. {@link #determine} reads the
     * bond's stored price to report the clean price it was given, and that price
     * is precisely what a price-from-yield calculation is solving for, so it
     * cannot be an input.
     *
     * @param bond            the bond; must not be {@code null}
     * @param calculationDate the purchase/settlement date; must not be
     *                        {@code null}
     * @param accruedInterest accrued interest from
     *                        {@code AccruedInterestService}, returned as it
     *                        stands — positive cum-interest, negative
     *                        ex-interest
     * @param couponPayments  the projected coupon payments; may be {@code null}
     * @return the accrued interest the buyer is charged, never {@code null}
     * @throws IllegalArgumentException when {@code bond},
     *                                  {@code calculationDate} or
     *                                  {@code accruedInterest} is {@code null}
     */
    BigDecimal accruedInterestCharged(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal accruedInterest,
            List<CouponPayment> couponPayments);
}
