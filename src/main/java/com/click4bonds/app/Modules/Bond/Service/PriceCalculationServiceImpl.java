package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.AccruedInterest;
import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link PriceCalculationService}.
 *
 * <h2>The calculation</h2>
 *
 * <pre>
 * dirty price = sum of receipt / (1 + ytm) ^ years(settlement, receipt date)
 * clean price = dirty price - accrued interest charged
 * price       = clean price / face value x 100
 * </pre>
 *
 * <p>
 * The receipts are the coupons the buyer is entitled to and the principal
 * repayments after the settlement date, taken from the same projection the YTM
 * engine solves over and discounted on the same day count. That is what makes
 * this the exact inverse of {@link YtmCalculationService}: the rate the solver
 * returns is the one at which those receipts, less the purchase consideration,
 * net to zero, so discounting them at that rate reproduces the consideration the
 * solver started from.
 *
 * <p>
 * The stored price is never read. The point of the calculation is to find that
 * price, so it cannot also be an input — and a bond that has no price yet is
 * exactly the case this engine exists to answer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCalculationServiceImpl implements PriceCalculationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** A price is quoted to a hundredth of a percent, as {@code Bond.price} is. */
    private static final int PRICE_SCALE = 2;

    private static final BigDecimal LOWEST_RATE = BigDecimal.valueOf(-1);

    private final BondCashFlowService bondCashFlowService;
    private final AccruedInterestService accruedInterestService;
    private final XirrCalculator xirrCalculator;

    @Override
    public BondPrice calculatePrice(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal annualYtm) {

        validate(bond, calculationDate, annualYtm);

        List<XirrCalculator.CashFlow> receipts = bondCashFlowService
                .generateReceiptCashFlows(bond, calculationDate);

        /*
         * A bond with nothing left to pay has no price to imply: an empty series
         * discounts to zero, which would read as a price of zero rather than as
         * the mistake it is. A state conflict rather than a bad request — the
         * caller asked a fair question about a bond that has run out of payments.
         */
        if (receipts.isEmpty()) {
            throw new ConflictException(
                    "Bond has no future cash flow to price: isin=" + bond.getIsin());
        }

        BigDecimal dirtyPrice = xirrCalculator.presentValue(
                receipts,
                calculationDate,
                annualYtm,
                BondDayCountBasis.of(bond));

        BigDecimal accruedInterestCharged = bondCashFlowService
                .accruedInterestCharged(bond, calculationDate);

        BigDecimal cleanPrice = dirtyPrice.subtract(accruedInterestCharged);

        /*
         * A very high yield discounts the receipts to nothing, and the clean
         * price then falls below the accrued interest the seller is owed — a
         * negative price. Refuse it rather than return a number no trade could
         * be done at. This one is the caller's number, so it is a bad request.
         */
        if (cleanPrice.signum() <= 0) {
            throw new BadRequestException(
                    "Yield of " + annualYtm
                            + " does not imply a positive price: isin=" + bond.getIsin()
                            + " cleanPrice=" + cleanPrice);
        }

        BigDecimal price = cleanPrice
                .multiply(HUNDRED)
                .divide(BondFaceValue.of(bond), PRICE_SCALE, RoundingMode.HALF_UP);

        AccruedInterest accruedInterest = accruedInterestService.accrue(bond, calculationDate);

        log.debug(
                "Price from yield: isin={} calculationDate={} ytm={} receipts={} dirtyPrice={} accruedInterestCharged={} cleanPrice={} price={}",
                bond.getIsin(),
                calculationDate,
                annualYtm,
                receipts.size(),
                dirtyPrice,
                accruedInterestCharged,
                cleanPrice,
                price);

        return new BondPrice(
                price,
                dirtyPrice,
                accruedInterest.amount(),
                accruedInterestCharged,
                accruedInterest.days());
    }

    private void validate(
            Bond bond,
            LocalDate calculationDate,
            BigDecimal annualYtm) {

        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }
        if (annualYtm == null) {
            throw new IllegalArgumentException("Yield cannot be null");
        }
        if (annualYtm.compareTo(LOWEST_RATE) <= 0) {
            throw new IllegalArgumentException(
                    "Yield must be greater than -1 (i.e. above -100%)");
        }
    }
}
