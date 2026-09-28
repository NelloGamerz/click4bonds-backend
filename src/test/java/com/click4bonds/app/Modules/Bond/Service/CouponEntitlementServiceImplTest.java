package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the entitlement convention implemented by
 * {@link CouponEntitlementServiceImpl}.
 *
 * <p>
 * The rule is:
 *
 * <pre>
 * entitled = paymentDate &gt; calculationDate
 *         &amp;&amp; (recordDate == null || calculationDate &lt;= recordDate)
 * </pre>
 *
 * <p>
 * If the business ever changes this convention, these tests are the only place
 * that needs to change alongside the service - the parser and the cash-flow
 * generator are unaffected.
 */
class CouponEntitlementServiceImplTest {

        /** The value used throughout the Satin Creditcare 2031 worked example. */
        private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 21);

        private CouponEntitlementService service;

        @BeforeEach
        void setUp() {
                service = new CouponEntitlementServiceImpl();
        }

        // ============================================================
        // BUYING BEFORE OR ON THE RECORD DATE
        // ============================================================

        @Test
        void entitledWhenHoldingOnTheRecordDate() {

                /*
                 * calculationDate is one day before the record date, so the
                 * investor is on the register when it closes.
                 */
                assertTrue(service.isCouponEntitled(
                                CALCULATION_DATE,
                                LocalDate.of(2026, 10, 8),
                                LocalDate.of(2026, 10, 23)));
        }

        @Test
        void entitledWhenHoldingExactlyOnTheRecordDate() {

                assertTrue(service.isCouponEntitled(
                                LocalDate.of(2026, 10, 8),
                                LocalDate.of(2026, 10, 8),
                                LocalDate.of(2026, 10, 23)));
        }

        // ============================================================
        // BUYING AFTER THE RECORD DATE
        // ============================================================

        @Test
        void notEntitledWhenTheRecordDateHasAlreadyPassed() {

                /*
                 * The 2026-09-23 coupon of a monthly bond whose record date is 15
                 * days earlier: the record date (2026-09-08) fell before the
                 * calculation date (2026-09-21), so that coupon belongs to the
                 * seller.
                 */
                assertFalse(service.isCouponEntitled(
                                CALCULATION_DATE,
                                LocalDate.of(2026, 9, 8),
                                LocalDate.of(2026, 9, 23)));
        }

        // ============================================================
        // PAYMENT DATE ALONE
        // ============================================================

        @Test
        void notEntitledForAPaymentThatIsNotInTheFuture() {

                assertFalse(service.isCouponEntitled(
                                CALCULATION_DATE,
                                null,
                                CALCULATION_DATE));

                assertFalse(service.isCouponEntitled(
                                CALCULATION_DATE,
                                null,
                                CALCULATION_DATE.minusDays(1)));
        }

        @Test
        void entitledForAFuturePaymentWhenNoRecordDateRuleExists() {

                /*
                 * Backward compatibility: bonds without record-date information
                 * keep the historical behaviour of including every future coupon.
                 */
                assertTrue(service.isCouponEntitled(
                                CALCULATION_DATE,
                                null,
                                CALCULATION_DATE.plusDays(2)));
        }

        @Test
        void recordDateRuleCannotOverrideThePaymentDateCheck() {

                /*
                 * A record date in the future must not resurrect a coupon that
                 * has already been paid.
                 */
                assertFalse(service.isCouponEntitled(
                                CALCULATION_DATE,
                                CALCULATION_DATE.plusDays(15),
                                CALCULATION_DATE.minusDays(1)));
        }

        // ============================================================
        // VALIDATION
        // ============================================================

        @Test
        void rejectsNullCalculationDate() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> service.isCouponEntitled(
                                                null,
                                                LocalDate.of(2026, 10, 8),
                                                LocalDate.of(2026, 10, 23)));
        }

        @Test
        void rejectsNullPaymentDate() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> service.isCouponEntitled(
                                                CALCULATION_DATE,
                                                LocalDate.of(2026, 10, 8),
                                                null));
        }
}
