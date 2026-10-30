package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;

/**
 * Tests for pricing a bond from a yield.
 *
 * <p>
 * Every test builds the real calculation services and computes values from bond
 * data rather than mocking the result, and every one uses a fixed settlement
 * date, so outcomes are reproducible on any day. Only the bond repository is
 * mocked, because the round-trip tests need the real YTM engine and that is the
 * one thing it writes to.
 *
 * <p>
 * The keystone here is the round trip: a price solved from a yield, fed back
 * through the yield engine, must return the yield it started from. That is the
 * property that makes this engine the inverse of the existing one rather than a
 * second, subtly different opinion about bond arithmetic.
 */
@ExtendWith(MockitoExtension.class)
class PriceCalculationServiceTest {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

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

    private PriceCalculationService priceService;
    private YtmCalculationService ytmCalculationService;

    @BeforeEach
    void setUp() {
        priceService = new PriceCalculationServiceImpl(
                cashFlowService,
                accruedService,
                xirrCalculator);

        ytmCalculationService = new YtmCalculationServiceImpl(
                cashFlowService,
                xirrCalculator,
                bondService);
    }

    // ------------------------------------------------------------------
    // The formula, verified against arithmetic that can be done by hand
    // ------------------------------------------------------------------

    /**
     * A bond with exactly one payment left isolates the discounting from every
     * other convention.
     *
     * <p>
     * Settled on a coupon date, so nothing has accrued and the clean price is the
     * whole of the price. One payment of 110 falls due in exactly 365 days, so
     * the year fraction is exactly 1 and the present value is 110/(1+y) with no
     * day-count rounding in the way. At a 10% yield that is exactly 100.
     */
    @Test
    void pricesASingleRemainingPaymentAtItsDiscountedValue() {

        Bond bond = oneYearBullet();
        LocalDate settlement = LocalDate.of(2026, 1, 1);

        BondPrice atTenPercent = priceService.calculatePrice(
                bond,
                settlement,
                new BigDecimal("0.10"));

        assertMoneyEquals("100.00", atTenPercent.price(), "clean price at a 10% yield");
        assertMoneyEquals("100.0000000000", atTenPercent.dirtyPrice(), "present value");
        assertEquals(0, atTenPercent.accruedDays(), "settled on a coupon date, so nothing accrued");
        assertMoneyEquals("0", atTenPercent.accruedInterestCharged(), "nothing accrued to charge");

        /*
         * And again at a rate where the answer is not a round number: 110/1.2.
         */
        BondPrice atTwentyPercent = priceService.calculatePrice(
                bond,
                settlement,
                new BigDecimal("0.20"));

        assertMoneyEquals("91.67", atTwentyPercent.price(), "clean price at a 20% yield");
    }

    /**
     * The accrued interest is what separates the dirty price from the clean one,
     * and the quoted price is the clean price restated as a percentage of par.
     */
    @Test
    void subtractsAccruedInterestFromThePresentValueToReachTheCleanPrice() {

        Bond bond = muthootFincorp2028();
        LocalDate settlement = LocalDate.of(2026, 9, 3);

        BondPrice price = priceService.calculatePrice(
                bond,
                settlement,
                new BigDecimal("0.090174"));

        BigDecimal cleanFromDirty = price.dirtyPrice().subtract(price.accruedInterestCharged());
        BigDecimal cleanFromQuote = price.price()
                .multiply(BondFaceValue.of(bond))
                .divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP);

        /*
         * The quoted price is stored to two decimals, so the two agree to within
         * half of the last one rather than exactly.
         */
        assertTrue(
                cleanFromQuote.subtract(cleanFromDirty).abs().compareTo(new BigDecimal("0.005")) <= 0,
                "Quoted " + cleanFromQuote + " against dirty less accrual " + cleanFromDirty);

        /*
         * The yield the platform's own integration test solves for this bond at a
         * price of 99.63 must price it back at 99.63.
         */
        assertEquals(
                0,
                new BigDecimal("99.63").compareTo(price.price()),
                "Expected the documented 9.0174% yield to imply a price of 99.63, got " + price.price());
    }

    // ------------------------------------------------------------------
    // The inverse property
    // ------------------------------------------------------------------

    /**
     * Price solved from a yield, then yield solved back from that price, is the
     * yield it started from.
     *
     * <p>
     * The price is rounded to two decimals on the way, as a stored price is, so
     * the yield that comes back is the yield of a bond that really trades — not
     * of an unrounded intermediate. The tolerance is a basis point.
     */
    @Test
    void roundTripsYieldThroughPriceForAnAmortizingBond() {

        Bond bond = stagedAmortizingBond();
        LocalDate settlement = LocalDate.of(2026, 9, 3);

        for (String yield : new String[] { "0.06", "0.100385", "0.1455" }) {

            BigDecimal target = new BigDecimal(yield);

            BondPrice price = priceService.calculatePrice(bond, settlement, target);

            /*
             * The YTM engine reads the price off the bond, so the solved price is
             * written there — which is how a real bond would be priced — and the
             * yield is solved from it.
             */
            bond.setPrice(price.price());

            BigDecimal recovered = ytmCalculationService.calculateYtm(bond, settlement);

            assertEquals(
                    target.doubleValue(),
                    recovered.doubleValue(),
                    0.0001,
                    "Yield " + yield + " round-tripped to " + recovered + " via price " + price.price());
        }
    }

    /**
     * The same round trip, but with the price supplied by the caller instead of
     * read off the bond.
     *
     * <p>
     * The bond carries a deliberately wrong stored price: if the new overload
     * read it, the yield would be nothing like 8%. Nothing is written to the
     * bond, which is the property a deal-scoped calculation relies on.
     */
    @Test
    void solvesTheYieldAtACallerSuppliedPriceWithoutTouchingTheBond() {

        Bond bond = muthootFincorp2028();
        bond.setPrice(new BigDecimal("42.00"));
        LocalDate settlement = LocalDate.of(2026, 9, 3);

        BigDecimal targetYield = new BigDecimal("0.08");

        BondPrice atEightPercent = priceService.calculatePrice(
                bond, settlement, targetYield);

        BigDecimal recovered = ytmCalculationService.previewAnnualYtm(
                bond, settlement, atEightPercent.price());

        assertEquals(
                targetYield.doubleValue(),
                recovered.movePointLeft(2).doubleValue(),
                0.0001,
                "Yield at the supplied price should be 8%, got " + recovered);

        assertEquals(
                0,
                new BigDecimal("42.00").compareTo(bond.getPrice()),
                "The bond's stored price must not change");
        assertNull(bond.getAnnualYtm(), "The bond's yield must not be written");
    }

    /**
     * A yield and the price it implies must move in opposite directions: a bond
     * that costs less is the one that pays more.
     */
    @Test
    void aHigherYieldImpliesALowerPrice() {

        Bond bond = muthootFincorp2028();
        LocalDate settlement = LocalDate.of(2026, 9, 3);

        BigDecimal atEight = priceService
                .calculatePrice(bond, settlement, new BigDecimal("0.08"))
                .price();
        BigDecimal atTen = priceService
                .calculatePrice(bond, settlement, new BigDecimal("0.10"))
                .price();

        assertTrue(
                atEight.compareTo(atTen) > 0,
                "A 8% yield should cost more than a 10% yield, got " + atEight + " and " + atTen);
    }

    // ------------------------------------------------------------------
    // The day count, which the two directions must share
    // ------------------------------------------------------------------

    /**
     * A Sovereign is discounted on 30/360 and a corporate bond on Actual/365, so
     * the same receipts at the same yield are worth different amounts.
     *
     * <p>
     * Over a half year the two conventions disagree about how much of a year has
     * passed — 180/360 against 184/365 — and the present value of 110 at 10% is
     * 100 x sqrt(1.1) on the 30/360 basis. Asserted on the present value, which
     * involves no accrual and so isolates the discounting.
     */
    @Test
    void discountsSovereignsOnThirtyThreeSixty() {

        Bond sovereign = oneYearBullet();
        sovereign.setRating("Sovereign");

        Bond corporate = oneYearBullet();
        corporate.setRating("AAA");

        LocalDate settlement = LocalDate.of(2026, 7, 1);

        BondPrice sovereignPrice = priceService.calculatePrice(
                sovereign,
                settlement,
                new BigDecimal("0.10"));
        BondPrice corporatePrice = priceService.calculatePrice(
                corporate,
                settlement,
                new BigDecimal("0.10"));

        assertMoneyEquals(
                "104.8808848170",
                sovereignPrice.dirtyPrice(),
                "110 discounted over a 30/360 half year at 10%");

        assertTrue(
                sovereignPrice.dirtyPrice().compareTo(corporatePrice.dirtyPrice()) > 0,
                "A 30/360 half year is shorter than 184/365, so it discounts less");
    }

    // ------------------------------------------------------------------
    // Cum- and ex-interest
    // ------------------------------------------------------------------

    /**
     * Bought before the record date the buyer receives the coupon and pays the
     * accrual; bought after it the coupon goes to the seller, so the accrual
     * turns negative and is set against the price.
     */
    @Test
    void chargesTheNegativeAccrualOnAnExInterestPurchase() {

        Bond bond = satinCreditcare2031();
        bond.setRecordDate(15);

        /*
         * The coupon on 2026-10-23 has a record date of 2026-10-08, so settling
         * on the 5th is cum-interest and on the 9th is ex-interest.
         */
        BondPrice cum = priceService.calculatePrice(
                bond,
                LocalDate.of(2026, 10, 5),
                new BigDecimal("0.12"));
        BondPrice ex = priceService.calculatePrice(
                bond,
                LocalDate.of(2026, 10, 9),
                new BigDecimal("0.12"));

        assertTrue(
                cum.accruedInterest().signum() > 0,
                "Interest has accrued by the 5th of October");
        assertMoneyEquals(
                cum.accruedInterest().toPlainString(),
                cum.accruedInterestCharged(),
                "a cum-interest purchase charges the accrual");

        assertTrue(
                ex.accruedInterest().signum() < 0,
                "The ex-interest accrual is negative");
        assertTrue(
                ex.accruedDays() < 0,
                "The ex-interest day count counts down to the coupon, so it is negative");
        assertMoneyEquals(
                ex.accruedInterest().toPlainString(),
                ex.accruedInterestCharged(),
                "an ex-interest purchase charges the negative accrual as it stands");

        assertTrue(
                ex.dirtyPrice().compareTo(cum.dirtyPrice()) < 0,
                "The ex-interest buyer also loses the 23rd's coupon, so pays less");
    }

    // ------------------------------------------------------------------
    // The price is not an input
    // ------------------------------------------------------------------

    /**
     * A bond with no price at all can still be priced — which is the case the
     * engine exists for — and a stale stored price cannot leak into the answer.
     */
    @Test
    void ignoresAnyStoredPrice() {

        Bond unpriced = oneYearBullet();
        Bond stale = oneYearBullet();
        stale.setPrice(new BigDecimal("42.00"));

        LocalDate settlement = LocalDate.of(2026, 1, 1);

        assertMoneyEquals(
                priceService.calculatePrice(unpriced, settlement, new BigDecimal("0.10")).price().toPlainString(),
                priceService.calculatePrice(stale, settlement, new BigDecimal("0.10")).price(),
                "the stored price must not influence the solved one");
    }

    /**
     * Every amount the projection produces is per unit of face value, and the
     * quoted price is a percentage of par, so the two cancel: the same bond in a
     * bigger lot yields the same quote.
     */
    @Test
    void quotesTheSamePriceWhateverTheLotSize() {

        Bond standard = oneYearBullet();

        Bond largeLot = oneYearBullet();
        largeLot.setLotSize(new BigDecimal("1000"));

        LocalDate settlement = LocalDate.of(2026, 1, 1);

        BondPrice standardPrice = priceService.calculatePrice(
                standard,
                settlement,
                new BigDecimal("0.10"));
        BondPrice largeLotPrice = priceService.calculatePrice(
                largeLot,
                settlement,
                new BigDecimal("0.10"));

        assertMoneyEquals("100.00", standardPrice.price(), "quoted price, Rs 100 lot");
        assertMoneyEquals("100.00", largeLotPrice.price(), "quoted price, Rs 1000 lot");

        assertMoneyEquals(
                "1000.0000000000",
                largeLotPrice.dirtyPrice(),
                "the present value itself is ten times larger");
    }

    // ------------------------------------------------------------------
    // Refusals
    // ------------------------------------------------------------------

    @Test
    void rejectsAMissingBondOrSettlementDateOrYield() {

        Bond bond = oneYearBullet();
        LocalDate settlement = LocalDate.of(2026, 1, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> priceService.calculatePrice(null, settlement, new BigDecimal("0.10")));

        assertThrows(
                IllegalArgumentException.class,
                () -> priceService.calculatePrice(bond, null, new BigDecimal("0.10")));

        assertThrows(
                IllegalArgumentException.class,
                () -> priceService.calculatePrice(bond, settlement, null));
    }

    /**
     * A rate of -100% or lower discounts every amount by an undefined factor, so
     * it is refused rather than producing an infinite price.
     */
    @Test
    void rejectsAYieldOfMinusOneHundredPercentOrLower() {

        Bond bond = oneYearBullet();
        LocalDate settlement = LocalDate.of(2026, 1, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> priceService.calculatePrice(bond, settlement, new BigDecimal("-1")));

        assertThrows(
                IllegalArgumentException.class,
                () -> priceService.calculatePrice(bond, settlement, new BigDecimal("-2.5")));
    }

    /**
     * A bond that has run out of payments has no price to imply. An empty series
     * would otherwise discount to zero and be reported as a price of zero.
     */
    @Test
    void refusesToPriceABondWithNoFuturePayment() {

        Bond bond = oneYearBullet();
        bond.setMaturityDate(LocalDate.of(2025, 1, 1));
        bond.setMaturityDescription("01/01/2025");

        ConflictException thrown = assertThrows(
                ConflictException.class,
                () -> priceService.calculatePrice(bond, LocalDate.of(2026, 1, 1), new BigDecimal("0.10")));

        assertNotNull(thrown.getMessage());
        assertTrue(
                thrown.getMessage().contains("TESTBULLET01"),
                "The refusal should name the bond: " + thrown.getMessage());
    }

    /**
     * At an absurd yield the receipts discount away to less than the accrued
     * interest owed on them, and the clean price would come out negative. That is
     * a fault in the caller's number, so it is a bad request.
     */
    @Test
    void refusesAYieldThatImpliesANegativePrice() {

        Bond bond = muthootFincorp2028();

        assertThrows(
                BadRequestException.class,
                () -> priceService.calculatePrice(
                        bond,
                        LocalDate.of(2026, 9, 3),
                        new BigDecimal("50")));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /**
     * A Rs 100 lot, 10% annual bullet with one coupon left, settled on a coupon
     * date one year before maturity.
     */
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

    /** The bond whose 9.0174% yield at a price of 99.63 the YTM tests document. */
    private Bond muthootFincorp2028() {

        Bond bond = new Bond();
        bond.setIsin("INE549K07BW6");
        bond.setName("Muthoot Fincorp Ltd 2028");
        bond.setPrice(new BigDecimal("99.63"));
        bond.setCouponRate(new BigDecimal("8.80"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2028, 2, 2));
        bond.setMaturityDescription("02/02/2028");
        bond.setIpDateDescription("02/02 Ann");
        return bond;
    }

    /** 12% monthly, whose coupons move in and out of the ex-interest window. */
    private Bond satinCreditcare2031() {

        Bond bond = new Bond();
        bond.setIsin("INE836B08319");
        bond.setName("Satin Creditcare Network 2031");
        bond.setPrice(new BigDecimal("98.94"));
        bond.setCouponRate(new BigDecimal("12.00"));
        bond.setCouponFrequency(CouponFrequency.MONTHLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2031, 7, 23));
        bond.setMaturityDescription("23/Jul/31");
        bond.setIpDateDescription("23rd of every month");
        return bond;
    }

    /** Half-yearly with staged IP amortization, so the coupons step down. */
    private Bond stagedAmortizingBond() {

        Bond bond = new Bond();
        bond.setIsin("STAGE0000001");
        bond.setName("Staged Amortizing Bond");
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.AMORTIZING);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");
        return bond;
    }

    /**
     * Value equality without the scale, because the same amount is legible at
     * more than one scale — {@code 100}, {@code 100.00} and {@code 100.0000000000}
     * are one answer.
     */
    private static void assertMoneyEquals(String expected, BigDecimal actual, String message) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                message + ": expected " + expected + " but was " + actual);
    }
}
