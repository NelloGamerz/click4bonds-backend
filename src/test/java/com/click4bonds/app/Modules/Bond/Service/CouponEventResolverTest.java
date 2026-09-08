package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.CouponEvent;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Unit tests for {@link CouponEventResolverImpl}.
 *
 * The resolver must reuse the existing {@link CouponDateGenerator} to produce
 * one {@link CouponEvent} per future coupon date and, until a record-date
 * source exists, leave entitlement dates unset (the current cum-coupon
 * fallback). No coupon amounts and no cash-flow changes are asserted here.
 */
class CouponEventResolverTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 8);

    private final CouponDateGenerator couponDateGenerator = new CouponDateGenerator();
    private final CouponEventResolver resolver =
            new CouponEventResolverImpl(couponDateGenerator);

    @Test
    void resolvesOneEventPerMonthlyCouponUpToMaturity() {
        Bond bond = monedoLikeBond();

        List<CouponEvent> events = resolver.resolve(bond, CALCULATION_DATE);

        // Coupons on the 19th from Sep 2026 (already after the 8th) through
        // Jul 2027 (maturity), all strictly after the calculation date.
        assertEquals(11, events.size());

        assertEquals(LocalDate.of(2026, 9, 19), events.get(0).couponDate());
        assertEquals(LocalDate.of(2027, 7, 19), events.get(events.size() - 1).couponDate());
    }

    @Test
    void eventsMatchRawCouponDatesAndAreOrdered() {
        Bond bond = monedoLikeBond();

        List<CouponEvent> events = resolver.resolve(bond, CALCULATION_DATE);
        List<LocalDate> rawDates = couponDateGenerator.generate(bond, CALCULATION_DATE);

        assertEquals(rawDates, events.stream().map(CouponEvent::couponDate).toList());
        assertTrue(isAscending(events));
    }

    @Test
    void entitlementDatesAreUnsetByDefault() {
        Bond bond = monedoLikeBond();

        List<CouponEvent> events = resolver.resolve(bond, CALCULATION_DATE);

        for (CouponEvent event : events) {
            assertNull(event.recordDate());
            assertNull(event.exCouponDate());
            assertFalse(event.hasEntitlementDates());
        }
    }

    @Test
    void returnsEmptyWhenNoCouponIsAfterCalculationDate() {
        Bond bond = monedoLikeBond();
        // Settling exactly on maturity leaves no future coupons.
        assertEquals(0, resolver.resolve(bond, LocalDate.of(2027, 7, 19)).size());
    }

    @Test
    void supportsRangeFrequenciesThroughTheSameGenerator() {
        Bond bond = new Bond();
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");

        List<CouponEvent> events = resolver.resolve(bond, CALCULATION_DATE);

        assertFalse(events.isEmpty());
        assertEquals(events.size(), couponDateGenerator.generate(bond, CALCULATION_DATE).size());
    }

    private static boolean isAscending(List<CouponEvent> events) {
        for (int i = 1; i < events.size(); i++) {
            if (!events.get(i).couponDate().isAfter(events.get(i - 1).couponDate())) {
                return false;
            }
        }
        return true;
    }

    private static Bond monedoLikeBond() {
        Bond bond = new Bond();
        bond.setCouponRate(new BigDecimal("13.70"));
        bond.setCouponFrequency(CouponFrequency.MONTHLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2027, 7, 19));
        bond.setIpDateDescription("19th of Every Month");
        bond.setMaturityDescription("19/03/2027 to 19/07/2027 (20% Monthly)");
        return bond;
    }
}
