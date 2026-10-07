package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;

/**
 * The year a Sovereign's yield is measured in.
 *
 * <p>A Sovereign is quoted on 30/360 and everything else on actual/365, so these
 * tests are written around the one fact that separates the two: 100 in and 110
 * out is a year's return when the calendar says a year has passed, and it is not
 * when the same span is 181 actual days long.</p>
 */
@ExtendWith(MockitoExtension.class)
class SovereignDayCountTest {

    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.100000");

    private final XirrCalculator xirrCalculator = new XirrCalculator();

    @Mock
    private BondCashFlowService bondCashFlowService;

    @Mock
    private XirrCalculator mockedXirrCalculator;

    @Mock
    private BondRepository bondRepository;

    // ======================
    // The basis itself
    // ======================

    @Test
    void thirtyThreeSixtyCountsACalendarYearAsExactlyOneYear() {

        System.out.println("TEST: 30/360 basis, one whole calendar year");

        BigDecimal rate = xirrCalculator.calculate(
                raiseOver(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
                XirrCalculator.DayCountBasis.THIRTY_360);

        System.out.println("Rate over 2026 : " + rate);

        assertEquals(TEN_PERCENT, rate);
    }

    @Test
    void thirtyThreeSixtyTreatsTheThirtyFirstAsTheThirtieth() {

        System.out.println("TEST: 30/360 basis, the 31st is the 30th");

        LocalDate purchase = LocalDate.of(2026, 1, 31);
        LocalDate redemption = LocalDate.of(2026, 7, 31);

        List<XirrCalculator.CashFlow> cashFlows = raiseOver(purchase, redemption);

        BigDecimal sovereignRate = xirrCalculator.calculate(
                cashFlows,
                XirrCalculator.DayCountBasis.THIRTY_360);
        BigDecimal corporateRate = xirrCalculator.calculate(cashFlows);

        System.out.println("Purchase       : " + purchase);
        System.out.println("Redemption     : " + redemption);
        System.out.println("Actual days    : " + java.time.temporal.ChronoUnit.DAYS
                .between(purchase, redemption));
        System.out.println("30/360 days    : 180 (31 Jan and 31 Jul are both 30ths)");
        System.out.println("Sovereign rate : " + sovereignRate);
        System.out.println("Corporate rate : " + corporateRate);

        /*
         * Six thirty-day months are half of a 360-day year, so the same cash
         * flows annualise to 1.10^2 - 1 rather than to the 10% a whole year
         * earns. The seventh month is a day short of a thirty-day month on this
         * basis, which is precisely why the two rates differ.
         */
        assertEquals(
                BigDecimal.valueOf(Math.pow(1.10, 2.0) - 1.0)
                        .setScale(6, RoundingMode.HALF_UP),
                sovereignRate);

        /*
         * The same span is 181 actual days, so the corporate basis is a hair
         * longer than half a year and yields a hair less.
         */
        assertEquals(
                BigDecimal.valueOf(Math.pow(1.10, 181.0 / 365.0) - 1.0)
                        .setScale(6, RoundingMode.HALF_UP),
                corporateRate);
    }

    @Test
    void theDefaultBasisForEveryOtherBondIsUnchanged() {

        System.out.println("TEST: default basis is still actual/365");

        LocalDate purchase = LocalDate.of(2026, 1, 31);
        LocalDate redemption = LocalDate.of(2026, 7, 31);

        List<XirrCalculator.CashFlow> cashFlows = raiseOver(purchase, redemption);

        /*
         * The single-argument call is what every existing caller uses; it must
         * stay the 365-day basis and nothing else.
         */
        assertEquals(
                xirrCalculator.calculate(cashFlows),
                xirrCalculator.calculate(cashFlows, XirrCalculator.DayCountBasis.ACTUAL_365));
    }

    @Test
    void rejectsAMissingBasis() {

        assertThrows(
                IllegalArgumentException.class,
                () -> xirrCalculator.calculate(
                        raiseOver(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 7, 31)),
                        null));
    }

    // ======================
    // Which basis the YTM service picks
    // ======================

    @Test
    void aSovereignRatedBondIsPricedOnThirtyThreeSixty() {

        Bond bond = createBond();
        bond.setRating("Sovereign");

        assertBasisUsed(bond, XirrCalculator.DayCountBasis.THIRTY_360);
    }

    @Test
    void everyOtherRatingKeepsTheDayCountItHasAlwaysHad() {

        /*
         * "Sovereign GOLD" is a credit label, not a government security: the
         * match is on the whole trimmed value, so it must not be read as one.
         */
        for (String rating : new String[] { null, "", "AAA", "Sovereign GOLD" }) {

            Bond bond = createBond();
            bond.setRating(rating);

            System.out.println("Rating " + rating + " keeps the actual/365 years");

            assertBasisUsed(bond, null);
        }
    }

    /**
     * Runs the YTM service for one bond and asserts which basis reached the
     * calculator: the given one when {@code expectedBasis} is set, and the
     * untouched single-argument call when it is null.
     */
    private void assertBasisUsed(Bond bond, XirrCalculator.DayCountBasis expectedBasis) {

        LocalDate calculationDate = LocalDate.of(2026, 9, 3);
        List<XirrCalculator.CashFlow> cashFlows = raiseOver(
                calculationDate,
                calculationDate.plusYears(1));
        BigDecimal expectedYtm = new BigDecimal("0.106947");

        when(bondCashFlowService.generateCashFlows(bond, calculationDate))
                .thenReturn(cashFlows);

        if (expectedBasis == null) {
            when(mockedXirrCalculator.calculate(cashFlows)).thenReturn(expectedYtm);
        } else {
            when(mockedXirrCalculator.calculate(cashFlows, expectedBasis))
                    .thenReturn(expectedYtm);
        }

        when(bondRepository.save(any(Bond.class))).thenReturn(bond);

        YtmCalculationService service = new YtmCalculationServiceImpl(
                bondCashFlowService,
                mockedXirrCalculator,
                bondRepository);

        System.out.println("Rating            : " + bond.getRating());
        System.out.println("Basis expected    : "
                + (expectedBasis == null ? "actual/365 (the default call)" : expectedBasis));

        assertEquals(expectedYtm, service.calculateYtm(bond, calculationDate));

        if (expectedBasis == null) {
            verify(mockedXirrCalculator).calculate(cashFlows);
        } else {
            verify(mockedXirrCalculator).calculate(cashFlows, expectedBasis);
        }
    }

    /** 100 in on the first date, 110 out on the second. */
    private static List<XirrCalculator.CashFlow> raiseOver(
            LocalDate purchase,
            LocalDate redemption) {

        return List.of(
                new XirrCalculator.CashFlow(purchase, new BigDecimal("-100")),
                new XirrCalculator.CashFlow(redemption, new BigDecimal("110")));
    }

    private static Bond createBond() {

        Bond bond = new Bond();

        bond.setName("Test Bond");
        bond.setIsin("TEST0000001");
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));

        return bond;
    }
}
