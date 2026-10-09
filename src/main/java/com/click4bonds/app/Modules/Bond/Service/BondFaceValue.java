package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * The rupee face value one unit of a bond carries.
 *
 * <p>
 * Every amount the projection produces — a coupon, a principal repayment, the
 * accrued interest, the clean price — is expressed per unit, and that unit's
 * face value is the base they are all measured from. The parts only reconcile
 * if they are all measured from the same base, so the base is derived here once
 * rather than written as a literal in each service.
 *
 * <p>
 * The base is the bond's lot size: a {@code 10 Lakh Lot} is Rs 10,00,000 of
 * face value, and a coupon on it is a coupon on that. A bond with no lot size
 * — {@code null}, or the {@code DEMAT}/{@code SGL} rows the parser leaves blank
 * — keeps the Rs 100 base, which is exactly what every amount was computed on
 * before lot sizes were applied. That is what makes the change backward
 * compatible.
 *
 * <p>
 * Scaling every amount by the lot size leaves the yield untouched: XIRR is a
 * rate, and a rate is unchanged when both sides of the series are multiplied by
 * the same number. So this changes what the schedule reports, never what the
 * bond yields.
 */
final class BondFaceValue {

    /**
     * The par a price is quoted against.
     *
     * <p>
     * A price of {@code 98.94} is 98.94% of par, and par is Rs 100 — not the
     * face value of the unit being priced. The face value is what the
     * percentage is then applied to.
     */
    static final BigDecimal PAR = BigDecimal.valueOf(100);

    private static final int SCALE = 10;

    private BondFaceValue() {
    }

    /**
     * The face value of one unit of {@code bond}, in rupees.
     *
     * <p>
     * A missing or non-positive lot size reads as Rs 100, which preserves the
     * historical base rather than rejecting the bond.
     */
    static BigDecimal of(Bond bond) {

        BigDecimal lotSize = bond == null ? null : bond.getLotSize();

        if (lotSize == null || lotSize.signum() <= 0) {
            return PAR;
        }

        return lotSize;
    }

    /**
     * The bond's clean price in rupees.
     *
     * <pre>
     * clean price = (price / 100) x face value
     * </pre>
     *
     * <p>
     * The stored price is a percentage of par, so it is applied to the unit's
     * face value to land on the same base as the coupons. Left as written, a
     * Rs 98.94 price would buy the whole lot that those coupons are quoted
     * against.
     *
     * @return the price in rupees, or {@code null} when the bond has no price
     */
    static BigDecimal cleanPrice(Bond bond) {

        BigDecimal price = bond == null ? null : bond.getPrice();

        if (price == null) {
            return null;
        }

        return price.multiply(of(bond))
                .divide(PAR, SCALE, RoundingMode.HALF_UP);
    }
}
