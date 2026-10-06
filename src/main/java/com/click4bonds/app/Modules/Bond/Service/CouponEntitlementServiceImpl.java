package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * Default {@link CouponEntitlementService}.
 *
 * <h2>Business rule implemented here</h2>
 *
 * <p>
 * The investor must hold the bond on or before the record date to receive the
 * coupon:
 *
 * <pre>
 * entitled = !calculationDate.isAfter(recordDate)
 *          = calculationDate &lt;= recordDate
 * </pre>
 *
 * <p>
 * Worked example for a monthly bond whose description is
 * {@code "15 days prior to interest payment date"}:
 *
 * <pre>
 * calculationDate = 2026-09-21
 *
 * paymentDate 2026-09-23 -> recordDate 2026-09-08 -> NOT entitled
 *                          (record date already passed on the purchase date)
 * paymentDate 2026-10-23 -> recordDate 2026-10-08 -> entitled
 * paymentDate 2026-11-23 -> recordDate 2026-11-08 -> entitled
 * </pre>
 *
 * <p>
 * A coupon is still dated on its payment date. Only the 2026-09-23 coupon is
 * removed from the projection, not moved to 2026-09-08.
 *
 * <h2>Bonds without a record-date rule are unaffected</h2>
 *
 * <p>
 * When {@code Bond.recordDateDescription} is absent / {@code "NA"} the parser
 * returns no record date and the pre-existing behaviour is preserved exactly:
 * every coupon paid after the calculation date is included. This keeps YTM
 * unchanged for all historical data that carries no record-date information.
 *
 * <h2>Known open convention, deliberately not changed here</h2>
 *
 * <p>
 * When the investor is <em>not</em> entitled to the next coupon, the purchase
 * leg is still priced as clean price + accrued interest. Whether a purchase
 * after the record date should be treated as an ex-coupon trade (clean price
 * only) has not been confirmed by the business, so accrued interest is left
 * untouched. Only the coupon inclusion is affected here.
 */
@Service
public class CouponEntitlementServiceImpl implements CouponEntitlementService {

    @Override
    public boolean isCouponEntitled(
            LocalDate calculationDate,
            LocalDate recordDate,
            LocalDate paymentDate) {

        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }

        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date cannot be null");
        }

        /*
         * A coupon paid on or before the calculation date is not a future cash
         * flow of the position being valued. This is the pre-existing rule and
         * applies whether or not a record date was resolved.
         */
        if (!paymentDate.isAfter(calculationDate)) {
            return false;
        }

        /*
         * No record-date rule for this bond: the source data carries no
         * entitlement information, so preserve the historical behaviour of
         * including the coupon.
         */
        if (recordDate == null) {
            return true;
        }

        return !calculationDate.isAfter(recordDate);
    }
}
