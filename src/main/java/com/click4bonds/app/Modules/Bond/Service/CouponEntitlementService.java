package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;

/**
 * Decides whether the investor represented by a YTM calculation date is
 * entitled to a particular coupon payment.
 *
 * <p>
 * This is deliberately separate from {@link RecordDateParser}: the parser knows
 * <em>when</em> the record date is, this service knows <em>what that means</em>
 * for entitlement. Settlement conventions differ between markets and venues, so
 * the rule lives in exactly one place and can be changed without touching the
 * parser or the cash-flow generator.
 *
 * <p>
 * The decision affects only <strong>whether</strong> a coupon cash flow is
 * included. It never moves a coupon: a coupon is always dated on its payment
 * date.
 */
public interface CouponEntitlementService {

    /**
     * @param calculationDate the date the YTM is calculated for, i.e. the
     *                        purchase/settlement date of the position
     * @param recordDate      the record date for this payment, or {@code null}
     *                        when the bond has no record-date rule
     * @param paymentDate     the coupon payment date; must not be {@code null}
     * @return {@code true} when the coupon cash flow belongs to the investor
     * @throws IllegalArgumentException when {@code calculationDate} or
     *                                  {@code paymentDate} is {@code null}
     */
    boolean isCouponEntitled(
            LocalDate calculationDate,
            LocalDate recordDate,
            LocalDate paymentDate);
}
