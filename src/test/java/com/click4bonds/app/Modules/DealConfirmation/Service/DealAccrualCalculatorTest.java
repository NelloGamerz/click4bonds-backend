package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.AccruedInterestService;
import com.click4bonds.app.Modules.Bond.Service.CouponScheduleService;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealAccrual;

/**
 * The interest figures the letter prints, and the cases where there are none.
 *
 * <p>The empty results matter as much as the populated ones. This runs while a
 * purchase is being recorded, on whatever data the bond was imported with, and
 * no accrual problem is worth failing a deal that has already been validated and
 * reserved.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealAccrualCalculatorTest {

    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 9, 25);
    private static final LocalDate PREVIOUS_COUPON = LocalDate.of(2026, 7, 23);

    @Mock
    private AccruedInterestService accruedInterestService;

    @Mock
    private CouponScheduleService couponScheduleService;

    private DealAccrualCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DealAccrualCalculator(
                accruedInterestService,
                couponScheduleService);
    }

    @Test
    void countsDaysFromThePreviousCouponToTheValueDate() {

        givenScheduleResolves();
        givenAccruedInterest("2.401");

        DealAccrual accrual = calculator.calculate(bond(), VALUE_DATE).orElseThrow();

        assertEquals(PREVIOUS_COUPON, accrual.previousCouponDate());
        assertEquals(64L, accrual.accruedDays());
        assertEquals(new BigDecimal("2.401"), accrual.accruedInterestPerHundredFace());
    }

    @Test
    void countsNothingWhenThereIsNoPreviousCoupon() {

        /*
         * A bond whose schedule has no date on or before the value date — a
         * newly issued one, for instance. The days are zero rather than the
         * whole period, and the last-payment date prints blank.
         */
        when(couponScheduleService.resolve(any(), any()))
                .thenReturn(new CouponScheduleService.CouponSchedule(null, VALUE_DATE));

        givenAccruedInterest("0");

        DealAccrual accrual = calculator.calculate(bond(), VALUE_DATE).orElseThrow();

        assertEquals(0L, accrual.accruedDays());
        assertEquals(null, accrual.previousCouponDate());
    }

    @Test
    void treatsAMissingAccruedInterestFigureAsZero() {

        givenScheduleResolves();

        when(accruedInterestService.calculate(any(), any())).thenReturn(null);

        DealAccrual accrual = calculator.calculate(bond(), VALUE_DATE).orElseThrow();

        assertEquals(BigDecimal.ZERO, accrual.accruedInterestPerHundredFace());
    }

    // =========================================================
    // NO ACCRUAL
    // =========================================================

    @Test
    void reportsNoAccrualForABondWithNoCouponRate() {

        /*
         * The service below dereferences the rate directly, so a bond without
         * one would throw where the caller expects a result. Imported bonds are
         * not guaranteed to have every field, and this fixtures the shape the
         * writer tests use.
         */
        Bond bond = bond();
        bond.setCouponRate(null);

        Optional<DealAccrual> accrual = calculator.calculate(bond, VALUE_DATE);

        assertTrue(accrual.isEmpty());
        verifyNoInteractions(couponScheduleService);
        verifyNoInteractions(accruedInterestService);
    }

    @Test
    void reportsNoAccrualWhenTheCouponScheduleCannotBeResolved() {

        /*
         * The coupon machinery signals an unparseable interest-payment
         * description by throwing. Caught here rather than propagated: this runs
         * inside deal creation, and an imported bond with an odd schedule must
         * not stop a purchase.
         */
        when(couponScheduleService.resolve(any(), any()))
                .thenThrow(new IllegalStateException("Invalid coupon period"));

        Optional<DealAccrual> accrual = calculator.calculate(bond(), VALUE_DATE);

        assertTrue(accrual.isEmpty());
    }

    @Test
    void toleratesMissingInputs() {

        assertFalse(calculator.calculate(null, VALUE_DATE).isPresent());
        assertFalse(calculator.calculate(bond(), null).isPresent());
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private void givenScheduleResolves() {

        when(couponScheduleService.resolve(any(), any()))
                .thenReturn(new CouponScheduleService.CouponSchedule(
                        PREVIOUS_COUPON,
                        LocalDate.of(2026, 10, 23)));
    }

    private void givenAccruedInterest(String amount) {

        when(accruedInterestService.calculate(any(), any()))
                .thenReturn(new BigDecimal(amount));
    }

    private Bond bond() {

        return Bond.builder()
                .id(java.util.UUID.randomUUID())
                .name("TEST BOND 2027")
                .isin("INE123A07012")
                .couponRate(new BigDecimal("13.70"))
                .build();
    }
}
