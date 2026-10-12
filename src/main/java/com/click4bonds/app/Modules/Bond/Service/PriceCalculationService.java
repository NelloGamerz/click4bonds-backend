package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Prices a bond from a yield to maturity.
 *
 * <p>
 * The engine the platform already had answers "what does this bond yield at this
 * price?" by solving for the rate at which the cash flow series — price included
 * — nets to nothing. This one answers the other question, "what price does this
 * bond have to be to yield this?", by discounting the same receipts at the rate
 * instead of solving for it.
 *
 * <p>
 * The two are one calculation read in two directions, and are deliberately built
 * from the same projection and the same day count so that they agree: see
 * {@link BondCashFlowService#generateReceiptCashFlows} and
 * {@link BondDayCountBasis}.
 *
 * <p>
 * Nothing is persisted. A bond's price is market data; see
 * {@link BondPrice}.
 */
public interface PriceCalculationService {

    /**
     * The price that yields {@code annualYtm} from {@code calculationDate}.
     *
     * @param bond            the bond; must not be {@code null}
     * @param calculationDate the settlement date the price is as of; must not be
     *                        {@code null}
     * @param annualYtm       the annual yield as a decimal, e.g. {@code 0.125}
     *                        for 12.5% — the form
     *                        {@link YtmCalculationService#calculateYtm} returns.
     *                        Must be above {@code -1}
     * @return the implied price and the amounts behind it
     * @throws IllegalArgumentException when an argument is {@code null} or the
     *                                  yield is {@code -100%} or lower — a
     *                                  precondition, since the caller has the
     *                                  request in front of it
     * @throws BadRequestException      when the yield implies a price that is not
     *                                  positive, which is a fault in the number
     *                                  the caller chose
     * @throws ConflictException        when the bond has no future cash flow to
     *                                  price, so there is no price to imply
     */
    BondPrice calculatePrice(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal annualYtm);
}
