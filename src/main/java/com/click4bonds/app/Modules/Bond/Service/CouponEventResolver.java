package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Dto.CouponEvent;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Resolves the sequence of {@link CouponEvent}s for a bond.
 *
 * <p>This is the intended seam for future record-date / ex-coupon logic: it
 * turns the raw coupon dates into events that can later be enriched with
 * {@code recordDate} / {@code exCouponDate} and used to decide whether the
 * buyer on a settlement date is entitled to each coupon.
 *
 * <p>Until a record-date source is introduced, implementations must fall back
 * to the current behaviour — one {@link CouponEvent} per future coupon date
 * with no entitlement dates set (see {@link CouponEvent#hasEntitlementDates()},
 * which is {@code false} in that case).
 */
public interface CouponEventResolver {

    /**
     * Builds the coupon events for a bond strictly after the calculation date
     * and up to (inclusive of) maturity, matching {@code CouponDateGenerator}.
     *
     * @param bond            the bond
     * @param calculationDate the reference date; only events after this date
     *                        are returned
     * @return the ordered coupon events; never {@code null}
     */
    List<CouponEvent> resolve(Bond bond, LocalDate calculationDate);
}
