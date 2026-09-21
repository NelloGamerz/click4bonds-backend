package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PurchaseConsideration;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.PurchasePriceTreatment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Pins the settlement convention:
 *
 * <pre>
 * buyer entitled to the first future coupon -&gt; CUM_INTEREST  -&gt; clean + accrued
 * buyer not entitled                         -&gt; EX_INTEREST   -&gt; clean only
 * </pre>
 *
 * <p>
 * The decision must come from the first future coupon's own record date, never
 * from a bond-level flag, so the per-coupon case below proves the treatment
 * moves with the coupon being considered.
 */
class PurchaseConsiderationServiceImplTest {

        /** The record-date rule used throughout the Satin Creditcare example. */
        private static final String FIFTEEN_DAYS_PRIOR = "15 days prior to interest payment date";

        private static final BigDecimal CLEAN_PRICE = new BigDecimal("98.94");

        /** 29 of 31 days from 2026-08-23 to 2026-09-23, on a 1.00 monthly coupon. */
        private static final BigDecimal ACCRUED_AT_SEPT_21 = new BigDecimal("0.93548387096774193548");

        /** Accrued interest that accrues toward the 2026-10-23 coupon. */
        private static final BigDecimal ACCRUED_AT_OCT_09 = new BigDecimal("0.53333333333333333333");

        private PurchaseConsiderationService service;

        @BeforeEach
        void setUp() {
                service = new PurchaseConsiderationServiceImpl(
                                new RecordDateParserImpl(),
                                new CouponEntitlementServiceImpl());
        }

        // ============================================================
        // BEFORE THE RECORD DATE -> CUM-INTEREST
        // ============================================================

        @Test
        void cumInterestWhenCalculationDateIsBeforeTheRecordDate() {

                /*
                 * 2026-09-21 <= 2026-10-08, so the buyer receives the coupon.
                 */
                PurchaseConsideration result = determine(
                                LocalDate.of(2026, 9, 21),
                                new BigDecimal("0.51612903225806451613"),
                                List.of(coupon(LocalDate.of(2026, 10, 23))));

                assertEquals(PurchasePriceTreatment.CUM_INTEREST, result.treatment());
                assertFalse(result.isExInterest());
                assertEquals(
                                0,
                                result.purchaseConsideration()
                                                .compareTo(new BigDecimal("99.45612903225806451613")),
                                "Cum-interest consideration must include accrued interest");
        }

        // ============================================================
        // ON THE RECORD DATE -> CUM-INTEREST
        // ============================================================

        @Test
        void cumInterestWhenCalculationDateIsExactlyTheRecordDate() {

                /*
                 * Holding on the record date is enough, so 2026-10-08 is still
                 * cum-interest even though it is the last possible day.
                 */
                PurchaseConsideration result = determine(
                                LocalDate.of(2026, 10, 8),
                                new BigDecimal("0.51612903225806451613"),
                                List.of(coupon(LocalDate.of(2026, 10, 23))));

                assertEquals(PurchasePriceTreatment.CUM_INTEREST, result.treatment());
                assertEquals(
                                0,
                                result.purchaseConsideration()
                                                .compareTo(new BigDecimal("99.45612903225806451613")));
        }

        // ============================================================
        // AFTER THE RECORD DATE -> EX-INTEREST
        // ============================================================

        @Test
        void exInterestWhenCalculationDateIsAfterTheRecordDate() {

                /*
                 * 2026-10-09 > 2026-10-08, so the coupon belongs to the seller and
                 * the accrued interest on it must not be charged.
                 */
                PurchaseConsideration result = determine(
                                LocalDate.of(2026, 10, 9),
                                ACCRUED_AT_OCT_09,
                                List.of(coupon(LocalDate.of(2026, 10, 23))));

                assertEquals(PurchasePriceTreatment.EX_INTEREST, result.treatment());
                assertTrue(result.isExInterest());

                assertEquals(
                                0,
                                result.accruedInterestCharged().compareTo(BigDecimal.ZERO),
                                "Ex-interest must charge no accrued interest");

                assertEquals(
                                0,
                                result.purchaseConsideration().compareTo(CLEAN_PRICE),
                                "Ex-interest consideration must be the clean price only");

                assertEquals(
                                0,
                                result.purchaseCashFlow().compareTo(CLEAN_PRICE.negate()),
                                "The XIRR purchase cash flow must be the negative clean price");

                assertEquals(LocalDate.of(2026, 10, 23), result.upcomingPaymentDate());
                assertEquals(LocalDate.of(2026, 10, 8), result.upcomingRecordDate());
        }

        // ============================================================
        // THE TREATMENT IS PER COUPON
        // ============================================================

        @Test
        void treatmentFollowsTheFirstFutureCouponNotTheBond() {

                LocalDate calculationDate = LocalDate.of(2026, 10, 9);

                /*
                 * The October coupon's record date has passed...
                 */
                PurchaseConsideration october = determine(
                                calculationDate,
                                ACCRUED_AT_OCT_09,
                                List.of(
                                                coupon(LocalDate.of(2026, 10, 23)),
                                                coupon(LocalDate.of(2026, 11, 23))));

                assertEquals(
                                PurchasePriceTreatment.EX_INTEREST,
                                october.treatment(),
                                "The first future coupon (2026-10-23) is ex-interest");

                /*
                 * ...but the November coupon's record date has not, so the same
                 * bond on the same calculation date is cum-interest with respect to
                 * that coupon. This is what a single bond-level record-date flag
                 * could not express.
                 */
                PurchaseConsideration november = determine(
                                calculationDate,
                                ACCRUED_AT_OCT_09,
                                List.of(coupon(LocalDate.of(2026, 11, 23))));

                assertEquals(
                                PurchasePriceTreatment.CUM_INTEREST,
                                november.treatment(),
                                "The 2026-11-23 coupon is still cum-interest");
        }

        @Test
        void ignoresCouponsThatAreNotInTheFuture() {

                /*
                 * Only future coupons can make a purchase ex-interest. A coupon on
                 * or before the calculation date is not part of the projection.
                 */
                PurchaseConsideration result = determine(
                                LocalDate.of(2026, 9, 21),
                                ACCRUED_AT_SEPT_21,
                                List.of(
                                                coupon(LocalDate.of(2026, 8, 23)),
                                                coupon(LocalDate.of(2026, 9, 21))));

                assertEquals(PurchasePriceTreatment.CUM_INTEREST, result.treatment());
                assertNull(result.upcomingPaymentDate());
        }

        // ============================================================
        // NO RECORD-DATE RULE -> UNCHANGED BEHAVIOUR
        // ============================================================

        @Test
        void noRecordDateRuleNeverProducesExInterest() {

                Bond bond = satinBond2031();
                bond.setRecordDateDescription("NA");

                PurchaseConsideration result = service.determine(
                                bond,
                                LocalDate.of(2026, 10, 9),
                                ACCRUED_AT_OCT_09,
                                List.of(coupon(LocalDate.of(2026, 10, 23))));

                assertEquals(
                                PurchasePriceTreatment.CUM_INTEREST,
                                result.treatment(),
                                "A bond without a record-date rule must never be ex-interest");

                assertEquals(
                                0,
                                result.purchaseConsideration()
                                                .compareTo(CLEAN_PRICE.add(ACCRUED_AT_OCT_09)),
                                "Accrued interest must still be charged");

                assertNull(result.upcomingRecordDate());
        }

        @Test
        void nullDescriptionNeverProducesExInterest() {

                Bond bond = satinBond2031();
                bond.setRecordDateDescription(null);

                PurchaseConsideration result = service.determine(
                                bond,
                                LocalDate.of(2026, 10, 9),
                                ACCRUED_AT_OCT_09,
                                List.of(coupon(LocalDate.of(2026, 10, 23))));

                assertEquals(PurchasePriceTreatment.CUM_INTEREST, result.treatment());
        }

        // ============================================================
        // VALIDATION
        // ============================================================

        @Test
        void rejectsNullArguments() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> service.determine(null, LocalDate.of(2026, 10, 9),
                                                ACCRUED_AT_OCT_09, List.of()));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> service.determine(satinBond2031(), null,
                                                ACCRUED_AT_OCT_09, List.of()));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> service.determine(satinBond2031(), LocalDate.of(2026, 10, 9),
                                                null, List.of()));
        }

        // ============================================================
        // HELPERS
        // ============================================================

        private PurchaseConsideration determine(
                        LocalDate calculationDate,
                        BigDecimal accruedInterest,
                        List<CouponPayment> couponPayments) {

                Bond bond = satinBond2031();
                bond.setRecordDateDescription(FIFTEEN_DAYS_PRIOR);

                return service.determine(bond, calculationDate, accruedInterest, couponPayments);
        }

        private static CouponPayment coupon(LocalDate paymentDate) {
                return new CouponPayment(paymentDate, new BigDecimal("1.00"), new BigDecimal("100"));
        }

        private static Bond satinBond2031() {

                Bond bond = new Bond();
                bond.setName("12% SATIN CREDITCARE NETWORK Ltd 2031");
                bond.setIsin("INE836B08319");
                bond.setPrice(CLEAN_PRICE);
                bond.setCouponRate(new BigDecimal("12.00"));
                bond.setCouponFrequency(CouponFrequency.MONTHLY);
                bond.setIpDateDescription("23rd of every month");
                bond.setMaturityType(MaturityType.FIXED);
                bond.setMaturityDate(LocalDate.of(2031, 7, 23));
                return bond;
        }
}
