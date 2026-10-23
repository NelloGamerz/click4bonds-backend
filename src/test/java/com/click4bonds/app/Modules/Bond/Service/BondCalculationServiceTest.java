package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Dto.BondCalculationResponse;
import com.click4bonds.app.Modules.Bond.Enums.CalculationType;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Tests for the endpoint that derives whichever half of the price/yield pair the
 * bond's own calculation type leaves open.
 *
 * <p>
 * The real engines are wired in, so a derived figure is the one the platform
 * would produce in production rather than a mock's echo. The bond lookup and the
 * repository are mocked; the repository is never reached, because nothing on this
 * path writes.
 */
@ExtendWith(MockitoExtension.class)
class BondCalculationServiceTest {

    private static final String ISIN = "TESTBULLET01";

    /** A coupon date exactly one year before maturity, so no interest has accrued. */
    private static final LocalDate SETTLEMENT = LocalDate.of(2026, 1, 1);

    @Mock
    private BondService bondService;

    private final CouponDateGenerator couponDateGenerator = new CouponDateGenerator();
    private final MaturityDescriptionParser parser = new MaturityDescriptionParserImpl();
    private final PrincipalRepaymentService principalService =
            new PrincipalRepaymentServiceImpl(parser);
    private final CouponCalculationService couponService = new CouponCalculationServiceImpl();
    private final CouponScheduleService couponScheduleService =
            new CouponScheduleService(couponDateGenerator);
    private final AccruedInterestService accruedService =
            new AccruedInterestServiceImpl(
                    couponScheduleService, parser, couponDateGenerator, principalService);
    private final BondCashFlowService cashFlowService =
            new BondCashFlowServiceImpl(
                    couponDateGenerator, principalService, couponService, accruedService);
    private final XirrCalculator xirrCalculator = new XirrCalculator();

    private BondCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new BondCalculationService(
                bondService,
                new YtmCalculationServiceImpl(cashFlowService, xirrCalculator, bondService),
                new PriceCalculationServiceImpl(cashFlowService, accruedService, xirrCalculator),
                accruedService);
    }

    /**
     * A bond created from a yield is missing its price, so that is what comes
     * back — and the yield it came from is echoed unchanged, since under
     * {@code BY_YTM} the yield is the input rather than a reading of the date.
     */
    @Test
    void derivesThePriceForABondCreatedFromAYield() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_YTM);
        bond.setAnnualYtm(new BigDecimal("10.0000"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BondCalculationResponse response = calculationService.calculate(ISIN, SETTLEMENT);

        assertEquals(CalculationType.BY_YTM, response.calculationType());
        assertEquals(ISIN, response.isin());
        assertEquals(SETTLEMENT, response.calculationDate());
        assertEquals(
                0,
                new BigDecimal("10.0000").compareTo(response.ytmPercentage()),
                "The stored yield is the input and must come back as it is");
        assertEquals(
                0,
                new BigDecimal("0.100000").compareTo(response.ytmDecimal()),
                "The decimal must be the percentage moved, not a second reading");
        assertEquals(
                0,
                new BigDecimal("100.00").compareTo(response.price()),
                "A 10% yield on a 110 payment one year out prices at 100, got " + response.price());
        assertEquals(0, response.accruedDays(), "Settlement is on a coupon date");
    }

    /** The other direction: the same bond, priced at 100, yielding 10%. */
    @Test
    void derivesTheYieldForABondCreatedFromAPrice() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_PRICE);
        bond.setPrice(new BigDecimal("100.00"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BondCalculationResponse response = calculationService.calculate(ISIN, SETTLEMENT);

        assertEquals(CalculationType.BY_PRICE, response.calculationType());
        assertEquals(
                0,
                new BigDecimal("100.00").compareTo(response.price()),
                "The stored price is the input and must come back as it is");
        assertEquals(
                0,
                new BigDecimal("10.0000").compareTo(response.ytmPercentage()),
                "A price of 100 on a 110 payment one year out yields 10%, got " + response.ytmPercentage());
        assertEquals(
                0,
                new BigDecimal("0.100000").compareTo(response.ytmDecimal()),
                "The decimal must be the percentage moved, not a second reading");
    }

    /**
     * The read stores nothing: the bond keeps the blank yield it had, so a lookup
     * cannot move the figure the platform persists through create and update.
     */
    @Test
    void storesNothingItDerives() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_PRICE);
        bond.setPrice(new BigDecimal("100.00"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BondCalculationResponse response = calculationService.calculate(ISIN, SETTLEMENT);

        assertNull(bond.getAnnualYtm(), "The derived yield must not be written to the bond");
        assertNull(bond.getYtmCalculatedAt(), "Nothing was persisted, so nothing is stamped");
        assertNull(
                response.calculatedAt(),
                "A yield derived for this response has no persistence time to report");
    }

    /**
     * A bond from before the column existed declares nothing. Its price says which
     * half was supplied, exactly as the create path reads it.
     */
    @Test
    void infersThePriceDirectionFromABondThatDeclaresNoType() {

        Bond bond = oneYearBullet();
        bond.setPrice(new BigDecimal("100.00"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BondCalculationResponse response = calculationService.calculate(ISIN, SETTLEMENT);

        assertEquals(CalculationType.BY_PRICE, response.calculationType());
        assertEquals(0, new BigDecimal("10.0000").compareTo(response.ytmPercentage()));
    }

    /** And the mirror: no price means the yield was the figure supplied. */
    @Test
    void infersTheYieldDirectionFromABondThatDeclaresNoType() {

        Bond bond = oneYearBullet();
        bond.setAnnualYtm(new BigDecimal("10.0000"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BondCalculationResponse response = calculationService.calculate(ISIN, SETTLEMENT);

        assertEquals(CalculationType.BY_YTM, response.calculationType());
        assertEquals(0, new BigDecimal("100.00").compareTo(response.price()));
    }

    /** Neither figure is held, so there is no source to derive the other from. */
    @Test
    void refusesABondThatHoldsNeitherFigure() {

        Bond bond = oneYearBullet();

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> calculationService.calculate(ISIN, SETTLEMENT));

        assertTrue(
                thrown.getMessage().contains("calculationType"),
                "The refusal should name what is missing: " + thrown.getMessage());
    }

    /**
     * A declared type naming a figure the bond does not hold is refused rather
     * than silently answered from the other half, which would derive from a source
     * the bond says it was not created from.
     */
    @Test
    void refusesAYieldDirectionWithNoStoredYield() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_YTM);
        bond.setPrice(new BigDecimal("99.25"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> calculationService.calculate(ISIN, SETTLEMENT));

        assertTrue(
                thrown.getMessage().contains("yield"),
                "The refusal should name the missing figure: " + thrown.getMessage());
    }

    @Test
    void refusesAPriceDirectionWithNoStoredPrice() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_PRICE);
        bond.setAnnualYtm(new BigDecimal("10.0000"));

        when(bondService.findBond(ISIN)).thenReturn(bond);

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> calculationService.calculate(ISIN, SETTLEMENT));

        assertTrue(
                thrown.getMessage().contains("price"),
                "The refusal should name the missing figure: " + thrown.getMessage());
    }

    /**
     * Round trip: a yield becomes a price through this endpoint, and that price
     * becomes the yield again — the two directions are inverses rather than two
     * unrelated readings.
     */
    @Test
    void roundTripsAYieldThroughTheDerivedPrice() {

        Bond fromYield = oneYearBullet();
        fromYield.setCalculationType(CalculationType.BY_YTM);
        fromYield.setAnnualYtm(new BigDecimal("7.5000"));

        when(bondService.findBond(ISIN)).thenReturn(fromYield);

        BigDecimal price = calculationService.calculate(ISIN, SETTLEMENT).price();

        Bond fromPrice = oneYearBullet();
        fromPrice.setCalculationType(CalculationType.BY_PRICE);
        fromPrice.setPrice(price);

        when(bondService.findBond(ISIN)).thenReturn(fromPrice);

        BigDecimal ytm = calculationService.calculate(ISIN, SETTLEMENT).ytmPercentage();

        assertEquals(
                7.5,
                ytm.doubleValue(),
                /*
                 * A one-year bond is the most yield-sensitive case there is: the
                 * derived price is rounded to a hundredth of a percent of par, and
                 * on a one-year money-back that half cent is worth about half a
                 * basis point of yield.
                 */
                0.01,
                "7.5% round-tripped to " + ytm + " via price " + price);
    }

    /** A Rs 100 lot, 10% annual bullet with one coupon left. */
    private Bond oneYearBullet() {

        Bond bond = new Bond();
        bond.setIsin(ISIN);
        bond.setName("One Year 10% Bullet");
        bond.setCouponRate(new BigDecimal("10.00"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2027, 1, 1));
        bond.setMaturityDescription("01/01/2027");
        bond.setIpDateDescription("01/01 Ann");
        return bond;
    }
}
