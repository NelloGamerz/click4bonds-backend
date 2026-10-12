package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.AccruedInterest;
import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
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
    void measuresTheMonthlyAccrualOverTheYearItsCouponIsPaidIn() {

        /*
         * The period runs from 15 February 2028 to 15 March 2028, so the
         * coupon is paid in the leap year 2028 and the period is measured over
         * 366 days. Five days of it are therefore measured over that same year:
         *
         * 100 × 12 × 5 / (100 × 366) = 0.16393442622950819672
         */
        BigDecimal result = service.calculate(
                bond("12.00", CouponFrequency.MONTHLY, "15th of every month", LocalDate.of(2028, 12, 15)),
                LocalDate.of(2028, 2, 20));
        assertClose(new BigDecimal("0.16393442622950819672"), result);
    }

    @Test
    void measuresTheCrossYearMonthlyAccrualOverTheYearItsCouponIsPaidIn() {

        /*
         * Accruing to the coupon paid on 17 January 2029, from the coupon paid
         * on 17 December 2028. That coupon is paid in 2029, which is not a leap
         * year, so the period — and the eight days accrued of it — are measured
         * over 365:
         *
         * 100 × 11 × 8 / (100 × 365) = 0.24109589041095890411
         */
        BigDecimal result = service.calculate(
                bond("11.00", CouponFrequency.MONTHLY, "17th of every month", LocalDate.of(2029, 2, 17)),
                LocalDate.of(2028, 12, 25));
        assertClose(new BigDecimal("0.24109589041095890411"), result);
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

    // ============================================================
    // RECORD PERIOD / EX-INTEREST
    // ============================================================

    /*
     * 12% monthly bond paying on the 17th, with a record date 15 days before
     * each payment date - the 2nd of the month. The coupon period used
     * throughout runs from 17 April 2026 to 17 May 2026, which is 30 days.
     */
    private static final LocalDate IP_DATE = LocalDate.of(2026, 5, 17);
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 5, 2);

    @Test
    void accruesNormallyBeforeTheRecordDate() {

        /*
         * 1 May is one day before the record date, so the buyer is still
         * entitled to the 17 May coupon and accrues for the 14 days since the
         * previous coupon:
         *
         * 100 × 12 × 14 / (100 × 365) = 0.46027397260273972603
         */
        AccruedInterest accrued = service.accrue(
                monthlyRecordDateBond(),
                RECORD_DATE.minusDays(1));

        assertEquals(14L, accrued.days());
        assertClose(new BigDecimal("0.46027397260273972603"), accrued.amount());
    }

    @Test
    void accruesNormallyOnTheRecordDate() {

        /*
         * The record date is the last day the buyer is entitled to the coupon,
         * which is the entitlement convention everywhere else in the engine, so
         * the accrual is still the ordinary one - 15 days here:
         *
         * 100 × 12 × 15 / (100 × 365) = 0.49315068493150684932
         */
        AccruedInterest accrued = service.accrue(monthlyRecordDateBond(), RECORD_DATE);

        assertEquals(15L, accrued.days());
        assertClose(new BigDecimal("0.49315068493150684932"), accrued.amount());
    }

    @Test
    void carriesTheAccrualNegativeFromTheDayAfterTheRecordDate() {

        /*
         * From the day after the record date the 17 May coupon belongs to the
         * seller, so the accrual is the part of that coupon covering the
         * fourteen days the buyer will hold without being paid - carried
         * negative:
         *
         * 100 × 12 × 14 / (100 × 365) = -0.46027397260273972603
         */
        AccruedInterest accrued = service.accrue(
                monthlyRecordDateBond(),
                RECORD_DATE.plusDays(1));

        assertEquals(-14L, accrued.days());
        assertClose(new BigDecimal("-0.46027397260273972603"), accrued.amount());
    }

    @Test
    void carriesTheSettlementThatReportedTwentyFiveDaysAsMinusFive() {

        /*
         * The case this was reported on: 12 May 2026 is 25 days into the period
         * and inside the record period. The calculator used to report 25 accrued
         * days, as if the buyer were entitled. It is five days from the coupon,
         * so the accrual is -5 days:
         *
         * 100 × 12 × 5 / (100 × 365) = -0.16438356164383561644
         */
        AccruedInterest accrued = service.accrue(
                monthlyRecordDateBond(),
                LocalDate.of(2026, 5, 12));

        assertEquals(-5L, accrued.days());
        assertClose(new BigDecimal("-0.16438356164383561644"), accrued.amount());
    }

    @Test
    void countsTheAccrualDownToZeroAsTheCouponApproaches() {

        Bond bond = monthlyRecordDateBond();

        assertEquals(-14L, service.accrue(bond, LocalDate.of(2026, 5, 3)).days());
        assertEquals(-13L, service.accrue(bond, LocalDate.of(2026, 5, 4)).days());
        assertEquals(-1L, service.accrue(bond, LocalDate.of(2026, 5, 16)).days());

        /*
         * On the coupon date itself there is no accrual at all: the period the
         * figure would cover has closed.
         */
        assertEquals(0L, service.accrue(bond, IP_DATE).days());
    }

    @Test
    void countsTheCalendarDaysWhenTheBondHasNoRecordDate() {

        /*
         * Without a record-date offset there is no record period, so the same
         * settlement keeps the calendar accrual it always had:
         *
         * 100 × 12 × 25 / (100 × 365) = 0.82191780821917808219
         */
        Bond bond = monthlyRecordDateBond();
        bond.setRecordDate(null);

        AccruedInterest accrued = service.accrue(bond, LocalDate.of(2026, 5, 12));

        assertEquals(25L, accrued.days());
        assertClose(new BigDecimal("0.82191780821917808219"), accrued.amount());
    }

    @Test
    void theAccrualReportedIsTheAccrualThePurchaseCharges() {

        /*
         * The invariant that was broken: the days and the amount the accrual
         * service reports must be the ones the price is built from. Reporting
         * them from two different decisions let a settlement be described as
         * accruing 25 days while the consideration charged none of them.
         */
        Bond bond = monthlyRecordDateBond();
        LocalDate settlement = LocalDate.of(2026, 5, 12);

        AccruedInterest accrued = service.accrue(bond, settlement);

        BigDecimal charged = new PurchaseConsiderationServiceImpl(
                new RecordDateParserImpl(),
                new CouponEntitlementServiceImpl())
                .accruedInterestCharged(
                        bond,
                        settlement,
                        accrued.amount(),
                        List.of(new CouponPayment(
                                IP_DATE,
                                new BigDecimal("1.00"),
                                new BigDecimal("100"))));

        assertEquals(accrued.amount(), charged);

        /*
         * And it is the negative figure that is charged, so the buyer pays the
         * clean price less it rather than paying for a coupon the seller keeps.
         */
        assertTrue(charged.signum() < 0);
    }

    private Bond monthlyRecordDateBond() {

        Bond bond = bond(
                "12.00",
                CouponFrequency.MONTHLY,
                "17th of every month",
                LocalDate.of(2028, 12, 17));

        bond.setRecordDate(15);

        return bond;
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
