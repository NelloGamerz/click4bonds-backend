package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.CouponEvent;

/**
 * Unit tests for the {@link CouponEvent} domain record (the record-date /
 * ex-coupon foundation). These assert the model's own contract only; they do
 * not depend on any coupon-generation or cash-flow behaviour.
 */
class CouponEventTest {

    private static final LocalDate COUPON_DATE = LocalDate.of(2026, 10, 19);

    @Test
    void ofCreatesEventWithOnlyCouponDate() {
        CouponEvent event = CouponEvent.of(COUPON_DATE);

        assertEquals(COUPON_DATE, event.couponDate());
        assertNull(event.recordDate());
        assertNull(event.exCouponDate());
        assertFalse(event.hasEntitlementDates());
    }

    @Test
    void allArgsConstructorSetsEntitlementDates() {
        LocalDate recordDate = LocalDate.of(2026, 10, 12);
        LocalDate exCouponDate = LocalDate.of(2026, 10, 9);

        CouponEvent event = new CouponEvent(COUPON_DATE, recordDate, exCouponDate);

        assertEquals(COUPON_DATE, event.couponDate());
        assertEquals(recordDate, event.recordDate());
        assertEquals(exCouponDate, event.exCouponDate());
        assertTrue(event.hasEntitlementDates());
    }

    @Test
    void nullCouponDateIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CouponEvent(null, null, null));
    }

    @Test
    void nullCouponDateViaFactoryIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CouponEvent.of(null));
    }

    @Test
    void recordDatesAreValueBased() {
        assertEquals(
                CouponEvent.of(COUPON_DATE),
                CouponEvent.of(COUPON_DATE));
    }

    @Test
    void entitlementDatesRequireBothRecordAndExDates() {
        CouponEvent onlyRecord = new CouponEvent(COUPON_DATE, LocalDate.of(2026, 10, 12), null);
        CouponEvent onlyEx = new CouponEvent(COUPON_DATE, null, LocalDate.of(2026, 10, 9));

        assertFalse(onlyRecord.hasEntitlementDates());
        assertFalse(onlyEx.hasEntitlementDates());
    }
}
