package com.click4bonds.app.Modules.Bond.Dto;

import java.time.LocalDate;

/**
 * A single coupon payment event on a bond, described by the calendar dates
 * that determine whether a buyer on a given settlement date is entitled to
 * the coupon.
 *
 * <p>This is the foundation for record-date / ex-coupon ("cum-coupon" vs
 * "ex-coupon") handling. It intentionally does <em>not</em> encode a record
 * date convention because none exists in the domain yet:
 *
 * <ul>
 *   <li>{@code couponDate} — the date the coupon is payable (the IP date).
 *   <li>{@code recordDate} — date used to identify the holders entitled to
 *       the coupon. May be {@code null} while not yet modelled.
 *   <li>{@code exCouponDate} — the first trading day on which a purchase no
 *       longer carries the next coupon. May be {@code null} while not yet
 *       modelled.
 * </ul>
 *
 * <p>When both {@code recordDate} and {@code exCouponDate} are {@code null}
 * the event has no entitlement dates and should be treated exactly as the
 * current behaviour: the coupon accrues to and is paid to the holder on the
 * coupon date (cum-coupon).
 *
 * <p>This record intentionally carries <b>no</b> coupon amount. Cash-flow
 * amounts remain the responsibility of {@code CouponPayment}, which already
 * models them together with the outstanding principal.
 *
 * @param couponDate    the payment date; never {@code null}
 * @param recordDate    the record date; {@code null} when undetermined
 * @param exCouponDate  the ex-coupon date; {@code null} when undetermined
 */
public record CouponEvent(
        LocalDate couponDate,
        LocalDate recordDate,
        LocalDate exCouponDate) {

    public CouponEvent {
        if (couponDate == null) {
            throw new IllegalArgumentException("Coupon date is required");
        }
    }

    /**
     * Factory for an event whose record/ex-coupon dates are not yet modelled.
     *
     * <p>This represents the current default (cum-coupon) behaviour and is the
     * fallback produced by {@code CouponEventResolver} until a record-date
     * source is introduced.
     *
     * @param couponDate the coupon payment date
     * @return a coupon event with no entitlement dates
     */
    public static CouponEvent of(LocalDate couponDate) {
        return new CouponEvent(couponDate, null, null);
    }

    /**
     * Whether record-date / ex-coupon dates have been determined for this
     * event. When {@code false}, coupon entitlement is assumed for the holder
     * on the coupon date (the current cum-coupon behaviour).
     */
    public boolean hasEntitlementDates() {
        return recordDate != null && exCouponDate != null;
    }
}
