package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Models.Bond;

class AccruedInterestServiceImplTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);
    private final AccruedInterestService service =
            new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator()));

    @Test
    void calculatesAnnualAccruedInterest() {
        assertClose(new BigDecimal("5.13534246575342"), service.calculate(
                bond("8.80", CouponFrequency.YEARLY, "02/02 Ann", LocalDate.of(2028, 2, 2)),
                CALCULATION_DATE));
    }

    @Test
    void returnsZeroOnCouponDate() {
        assertEquals(BigDecimal.ZERO, service.calculate(
                bond("8.80", CouponFrequency.YEARLY, "02/02 Ann", LocalDate.of(2028, 2, 2)),
                LocalDate.of(2026, 2, 2)));
    }

    @Test
    void calculatesHalfYearlyAccruedInterest() {
        BigDecimal result = service.calculate(
                bond("7.20", CouponFrequency.HALF_YEARLY, "26/03-26/09", LocalDate.of(2031, 9, 26)),
                CALCULATION_DATE);
        assertClose(new BigDecimal("1.58794520547945"), result);
    }

    @Test
    void calculatesQuarterlyAccruedInterest() {
        BigDecimal result = service.calculate(
                bond("8.00", CouponFrequency.QUARTERLY, "01/01-01/04", LocalDate.of(2028, 4, 1)),
                LocalDate.of(2026, 2, 1));
        assertClose(new BigDecimal("0.16986301369863"), result);
    }

    @Test
    void calculatesMonthlyAccruedInterest() {

        /*
         * 12% on face value 100, seventeen days into the 31-day period that
         * opens on 15 January 2026. A monthly non-Sovereign bond accrues over
         * the days of its period rather than over a twelfth of the year:
         *
         * 100 × 12 × 17 / (100 × 365) = 0.55890410958904109589
         */
        BigDecimal result = service.calculate(
                bond("12.00", CouponFrequency.MONTHLY, "15th of every month", LocalDate.of(2028, 12, 15)),
                LocalDate.of(2026, 2, 1));
        assertClose(new BigDecimal("0.55890410958904109589"), result);
    }

    @Test
    void measuresTheMonthlyAccrualOverTheYearItsPeriodOpensIn() {

        /*
         * The period runs from 15 February 2028 to 15 March 2028, so it opens
         * in the leap year 2028 and is measured over 366 days. Five days of it
         * are therefore measured over that same year, even though those five
         * days sit before 29 February:
         *
         * 100 × 12 × 5 / (100 × 366) = 0.16393442622950819672
         */
        BigDecimal result = service.calculate(
                bond("12.00", CouponFrequency.MONTHLY, "15th of every month", LocalDate.of(2028, 12, 15)),
                LocalDate.of(2028, 2, 20));
        assertClose(new BigDecimal("0.16393442622950819672"), result);
    }

    @Test
    void keepsThePeriodProRataForASovereignMonthlyBond() {

        /*
         * A Sovereign is quoted on 30/360 and keeps the flat twelfth of the
         * year: seventeen of the 31 days of a 1.00 coupon.
         */
        Bond bond = bond("12.00", CouponFrequency.MONTHLY, "15th of every month", LocalDate.of(2028, 12, 15));
        bond.setRating("Sovereign");

        assertClose(
                new BigDecimal("0.54838709677419354839"),
                service.calculate(bond, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void returnsZeroForZeroCouponAndMissingPreviousCoupon() {
        assertEquals(BigDecimal.ZERO, service.calculate(
                bond("0", CouponFrequency.YEARLY, "02/02 Ann", LocalDate.of(2028, 2, 2)), CALCULATION_DATE));
    }

    @Test
    void returnsZeroForAtMaturity() {
        assertEquals(BigDecimal.ZERO, service.calculate(
                bond("8.80", CouponFrequency.AT_MATURITY, "02/02 Ann", LocalDate.of(2028, 2, 2)), CALCULATION_DATE));
    }

    @Test
    void validatesInputsAndMaturity() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(null, CALCULATION_DATE));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                bond("8.80", CouponFrequency.YEARLY, "02/02 Ann", LocalDate.of(2028, 2, 2)), null));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                bond(null, CouponFrequency.YEARLY, "02/02 Ann", LocalDate.of(2028, 2, 2)), CALCULATION_DATE));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                bond("8.80", CouponFrequency.YEARLY, "02/02 Ann", LocalDate.of(2026, 9, 2)), CALCULATION_DATE));
    }

    private Bond bond(String rate, CouponFrequency frequency, String ipDescription, LocalDate maturity) {
        Bond bond = new Bond();
        bond.setCouponRate(rate == null ? null : new BigDecimal(rate));
        bond.setCouponFrequency(frequency);
        bond.setIpDateDescription(ipDescription);
        bond.setMaturityDate(maturity);
        return bond;
    }

        private void assertClose(BigDecimal expected, BigDecimal actual) {
                assertTrue(expected.subtract(actual).abs().compareTo(new BigDecimal("0.00000000000001")) < 0,
                                () -> "Expected " + expected + " but was " + actual);
        }
}
