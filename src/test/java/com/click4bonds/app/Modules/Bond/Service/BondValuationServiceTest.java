package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Enums.CalculationType;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Tests for filling in the half of the price/yield pair a bond was not created
 * from.
 *
 * <p>
 * The real engines are wired in, so a derived figure is the one the platform
 * would store in production rather than a mock's echo. Only the bond service
 * is mocked, because the yield engine writes through it.
 */
@ExtendWith(MockitoExtension.class)
class BondValuationServiceTest {

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

    private BondValuationService valuationService;

    @BeforeEach
    void setUp() {
        valuationService = new BondValuationService(
                new YtmCalculationServiceImpl(cashFlowService, xirrCalculator, bondService),
                new PriceCalculationServiceImpl(cashFlowService, accruedService, xirrCalculator));
    }

    /**
     * The bond is a 10% annual bullet settled on a coupon date exactly one year
     * before maturity, so the arithmetic is checkable by hand: one payment of 110
     * falls due in exactly a year, and at a 10% yield the price is 110/1.1 = 100.
     */
    @Test
    void derivesThePriceFromTheYield() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_YTM);
        bond.setAnnualYtm(new BigDecimal("10.0000"));

        valuationService.applyCalculationType(bond, SETTLEMENT);

        assertEquals(
                0,
                new BigDecimal("100.00").compareTo(bond.getPrice()),
                "Expected a 10% yield on a 110 payment one year out to price at 100, got " + bond.getPrice());

        assertEquals(
                0,
                new BigDecimal("9.7618").compareTo(bond.getSemiYtm()),
                "10% annual is 9.7618% semi-annual, got " + bond.getSemiYtm());
    }

    /** The other direction: the same bond, priced at 100, yielding 10%. */
    @Test
    void derivesTheYieldFromThePrice() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_PRICE);
        bond.setPrice(new BigDecimal("100.00"));

        valuationService.applyCalculationType(bond, SETTLEMENT);

        assertEquals(
                0,
                new BigDecimal("10.0000").compareTo(bond.getAnnualYtm()),
                "Expected a price of 100 on a 110 payment one year out to yield 10%, got " + bond.getAnnualYtm());

        assertEquals(
                0,
                new BigDecimal("9.7618").compareTo(bond.getSemiYtm()),
                "The yield derived from the price should carry its semi-annual reading too");

        assertNotNull(bond.getYtmCalculatedAt(), "A calculated yield should be stamped");
    }

    /**
     * The derived figure wins over one supplied for the same bond: two readings
     * calculated from different assumptions would be worse than one.
     */
    @Test
    void theDerivedFigureOverwritesTheSuppliedOne() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_YTM);
        bond.setAnnualYtm(new BigDecimal("10.0000"));
        bond.setPrice(new BigDecimal("42.00"));

        valuationService.applyCalculationType(bond, SETTLEMENT);

        assertEquals(
                0,
                new BigDecimal("100.00").compareTo(bond.getPrice()),
                "A supplied price must not survive a yield-derived one");
    }

    /**
     * No declared calculation type means no derivation, which is the behaviour
     * every existing client relies on.
     */
    @Test
    void leavesBothFiguresAloneWhenNoCalculationTypeIsDeclared() {

        Bond bond = oneYearBullet();
        bond.setPrice(new BigDecimal("99.25"));

        valuationService.applyCalculationType(bond, SETTLEMENT);

        assertNull(bond.getCalculationType(), "Nothing declared");
        assertEquals(0, new BigDecimal("99.25").compareTo(bond.getPrice()), "Price untouched");
        assertNull(bond.getAnnualYtm(), "No yield invented");
    }

    @Test
    void refusesToDeriveAPriceWithoutAYield() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_YTM);
        bond.setPrice(new BigDecimal("99.25"));

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> valuationService.applyCalculationType(bond, SETTLEMENT));

        assertTrue(
                thrown.getMessage().contains("annualYtm"),
                "The refusal should name the missing field: " + thrown.getMessage());
    }

    @Test
    void refusesToDeriveAYieldWithoutAPrice() {

        Bond bond = oneYearBullet();
        bond.setCalculationType(CalculationType.BY_PRICE);

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> valuationService.applyCalculationType(bond, SETTLEMENT));

        assertTrue(
                thrown.getMessage().contains("price"),
                "The refusal should name the missing field: " + thrown.getMessage());
    }

    /**
     * Round trip through the service that a create actually uses: a yield becomes
     * a price, and that price becomes the yield again.
     */
    @Test
    void roundTripsAYieldThroughTheDerivedPrice() {

        Bond fromYield = oneYearBullet();
        fromYield.setCalculationType(CalculationType.BY_YTM);
        fromYield.setAnnualYtm(new BigDecimal("7.5000"));
        valuationService.applyCalculationType(fromYield, SETTLEMENT);

        Bond fromPrice = oneYearBullet();
        fromPrice.setCalculationType(CalculationType.BY_PRICE);
        fromPrice.setPrice(fromYield.getPrice());
        valuationService.applyCalculationType(fromPrice, SETTLEMENT);

        assertEquals(
                7.5,
                fromPrice.getAnnualYtm().doubleValue(),
                /*
                 * A one-year bond is the most yield-sensitive case there is: the
                 * derived price is rounded to a hundredth of a percent of par, and
                 * on a one-year money-back that half cent is worth about half a
                 * basis point of yield.
                 */
                0.01,
                "7.5% round-tripped to " + fromPrice.getAnnualYtm() + " via price " + fromYield.getPrice());
    }

    /** A Rs 100 lot, 10% annual bullet with one coupon left. */
    private Bond oneYearBullet() {

        Bond bond = new Bond();
        bond.setIsin("TESTBULLET01");
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
