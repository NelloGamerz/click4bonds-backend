package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.AccruedInterestService;
import com.click4bonds.app.Modules.Bond.Service.CouponScheduleService;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealAccrual;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Works out the interest figures a deal confirmation prints.
 *
 * <p>Exists so that {@link AccruedInterestService} and
 * {@link CouponScheduleService} — both of which take a {@code Bond} <em>entity</em>
 * — are called inside the deal's transaction, on the managed bond, rather than
 * from the document step, which runs after the transaction has committed and is
 * forbidden from touching an entity.</p>
 *
 * <p>Reuses the existing services rather than reimplementing the arithmetic. The
 * platform already has one definition of accrued interest, and a second one
 * written for the printed letter would drift from the one used for pricing.</p>
 *
 * <p><strong>Never throws.</strong> A bond whose coupon schedule cannot be
 * resolved is a data problem, not a reason to refuse a purchase that has already
 * been validated and reserved. The caller receives an empty result and the
 * document step declines to generate a letter rather than printing a blank
 * interest figure into a legal document.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DealAccrualCalculator {

    private final AccruedInterestService accruedInterestService;
    private final CouponScheduleService couponScheduleService;

    /**
     * Computes the accrual for a bond as at the value date.
     *
     * @param bond      the bond being bought; may be null
     * @param valueDate settlement date the accrual is measured to
     * @return the figures, or empty when they cannot be determined
     */
    public Optional<DealAccrual> calculate(Bond bond, LocalDate valueDate) {

        if (bond == null || valueDate == null) {
            return Optional.empty();
        }

        /*
         * A bond with no coupon rate has no accrual to compute, and the service
         * below dereferences the rate directly. Checked here rather than letting
         * it throw, because a null rate is a property of imported data rather
         * than an exceptional condition.
         */
        if (bond.getCouponRate() == null) {

            log.debug(
                    "No coupon rate on bond {}, so no accrual to compute",
                    bond.getIsin());

            return Optional.empty();
        }

        try {

            CouponScheduleService.CouponSchedule schedule =
                    couponScheduleService.resolve(bond, valueDate);

            LocalDate previousCouponDate = schedule.previous();

            /*
             * Mirrors the expression AccruedInterestServiceImpl uses
             * internally. The same calculation rather than a new method on that
             * service, which would mean changing its interface and its tests to
             * expose a number it already computes for its own use.
             */
            long accruedDays = previousCouponDate == null
                    ? 0L
                    : ChronoUnit.DAYS.between(previousCouponDate, valueDate);

            BigDecimal accruedInterest =
                    accruedInterestService.calculate(bond, valueDate);

            return Optional.of(new DealAccrual(
                    previousCouponDate,
                    accruedDays,
                    accruedInterest == null ? BigDecimal.ZERO : accruedInterest));

        } catch (RuntimeException unpredictable) {

            /*
             * The coupon machinery reports an unresolvable schedule by throwing
             * IllegalStateException — for a bond whose interest-payment
             * description this application cannot parse, which is a real
             * possibility for imported data. Caught broadly on purpose: this
             * runs while a purchase is being recorded, and no accrual problem
             * should fail a deal that has already been validated and reserved.
             */
            log.warn(
                    "Could not compute accrual for bond {} as at {};"
                            + " the deal proceeds and no document will be generated",
                    bond.getIsin(),
                    valueDate,
                    unpredictable);

            return Optional.empty();
        }
    }
}
