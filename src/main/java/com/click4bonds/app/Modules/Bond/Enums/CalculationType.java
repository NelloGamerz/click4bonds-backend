package com.click4bonds.app.Modules.Bond.Enums;

/**
 * Which half of the price/yield pair a bond was created from.
 *
 * <p>
 * A price and a yield are two readings of one bond, and either can be derived
 * from the other — but only one of them can be the source. This names which one
 * the caller supplied, so the platform knows which one to work out:
 *
 * <pre>
 * BY_PRICE : price supplied  -> yield calculated and stored on annualYtm
 * BY_YTM   : yield supplied  -> price calculated and stored on price
 * </pre>
 *
 * <p>
 * The other half of the pair is never trusted when it disagrees: the derived
 * figure is written over it, because a supplied-and-kept companion would leave a
 * bond whose price and yield were calculated from different assumptions.
 *
 * <p>
 * Null means the caller did not say, and both halves are stored exactly as sent.
 * That is the behaviour every existing client already relies on, so leaving it
 * alone keeps them working.
 */
public enum CalculationType {

    /** The price is the input; the yield is derived from it. */
    BY_PRICE,

    /** The yield to maturity is the input; the price is derived from it. */
    BY_YTM
}
