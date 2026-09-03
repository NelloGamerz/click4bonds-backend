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
    private final AccruedInterestService service = new AccruedInterestServiceImpl(new CouponDateGenerator());

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
        BigDecimal result = service.calculate(
                bond("12.00", CouponFrequency.MONTHLY, "15th of every month", LocalDate.of(2028, 12, 15)),
                LocalDate.of(2026, 2, 1));
        assertClose(new BigDecimal("0.04657534246575"), result);
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
