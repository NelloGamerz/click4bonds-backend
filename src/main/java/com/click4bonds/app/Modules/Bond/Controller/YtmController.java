package com.click4bonds.app.Modules.Bond.Controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.AccruedInterest;
import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Dto.BondPriceResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondYtmResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.AccruedInterestService;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.PriceCalculationService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read access to a bond's yield to maturity, and to the price that yield implies.
 *
 * <p>
 * Kept apart from {@link BondController} because both endpoints are about the
 * bond's price/yield pair rather than its descriptive fields.
 *
 * <p>
 * The yield endpoint reports the figure already stored on the bond; the price
 * endpoint solves the opposite direction, for a yield the caller supplies. Both
 * rest on the same cash-flow schedule, so the two agree.
 */
@RestController
@RequestMapping("/api/bonds")
@RequiredArgsConstructor
@Slf4j
public class YtmController {

    /** A yield is quoted in percent; the engine works in decimals. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BondService bondService;
    private final PriceCalculationService priceCalculationService;
    private final AccruedInterestService accruedInterestService;

    /**
     * Reports the annual yield to maturity stored for the bond with this ISIN.
     *
     * <p>
     * Read-only: the yield is not solved for here. {@code annualYtm} is
     * calculated when a bond is created or updated — see
     * {@link com.click4bonds.app.Modules.Bond.Service.BondValuationService} — and
     * this endpoint returns that stored figure, so reading a yield can never move
     * the value it reports.
     *
     * @param isin            the bond's ISIN, case-insensitive.
     * @param calculationDate ISO date the accrued interest is measured to;
     *                        defaults to today, which is the usual settlement
     *                        horizon.
     * @return 404 when no bond carries that ISIN.
     */
    @GetMapping("/{isin}/ytm")
    public ResponseEntity<BondYtmResponse> getBondYtm(
            @PathVariable String isin,
            @RequestParam(required = false) LocalDate calculationDate) {

        LocalDate asOf = calculationDate != null
                ? calculationDate
                : LocalDate.now();

        log.info(
                "GET /bonds/{}/ytm - calculationDate={}",
                isin,
                asOf
        );

        Bond bond = bondService.findBond(isin);

        /*
         * Both representations are read off the one stored percentage. Moving the
         * decimal point is a change of representation, not a second calculation,
         * so the two can never disagree. A bond whose yield has never been
         * calculated has neither: null is the honest answer, where a zero would
         * read as a bond that yields nothing.
         */
        BigDecimal ytmPercentage = bond.getAnnualYtm();

        BigDecimal ytmDecimal = ytmPercentage == null
                ? null
                : ytmPercentage.divide(HUNDRED);

        /*
         * The accrual is the one figure here with no column behind it: it is a
         * function of the settlement date, which this call chooses rather than
         * reads. Computing it writes nothing, so the endpoint stays a read.
         */
        AccruedInterest accruedInterest = accruedInterestService.accrue(
                bond,
                asOf);

        return ResponseEntity.ok(
                new BondYtmResponse(
                        bond.getIsin(),
                        bond.getName(),
                        asOf,
                        ytmPercentage,
                        ytmDecimal,
                        bond.getPrice(),
                        accruedInterest.days(),
                        accruedInterest.amount(),
                        bond.getYtmCalculatedAt()
                )
        );
    }

    /**
     * Calculates the price the bond with this ISIN must trade at to yield a
     * given rate.
     *
     * <p>
     * The other direction of {@link #getBondYtm}. Unlike the yield lookup this
     * has no side effect: the bond's stored price is market data and is neither
     * read nor written, so a what-if cannot disturb it.
     *
     * @param isin            the bond's ISIN, case-insensitive.
     * @param ytm             the annual yield to price at, as a percentage —
     *                        {@code 12.5} means 12.5%. Required: there is no
     *                        sensible default yield to assume.
     * @param calculationDate ISO settlement date the price is as of; defaults to
     *                        today.
     * @return 400 when {@code ytm} is absent, is not a number, is {@code -100%}
     *         or lower, or implies a price that is not positive; 404 when no bond
     *         carries that ISIN; 409 when the bond has no future cash flow to
     *         price.
     */
    @GetMapping("/{isin}/price")
    public ResponseEntity<BondPriceResponse> getBondPrice(
            @PathVariable String isin,
            @RequestParam(required = false) BigDecimal ytm,
            @RequestParam(required = false) LocalDate calculationDate) {

        /*
         * Checked here rather than declared required, so a missing yield is a 400
         * with a message that says what to send instead of the container's
         * generic binding error.
         */
        if (ytm == null) {
            throw new BadRequestException(
                    "Query parameter 'ytm' is required: the annual yield as a percentage, e.g. ytm=12.5");
        }

        LocalDate asOf = calculationDate != null
                ? calculationDate
                : LocalDate.now();

        log.info(
                "GET /bonds/{}/price - ytm={}% calculationDate={}",
                isin,
                ytm,
                asOf
        );

        Bond bond = bondService.findBond(isin);

        /*
         * The engine works in decimals; the parameter is the percentage a caller
         * reads off a screen. Dividing by 100 is exact, so nothing is lost
         * crossing between them.
         */
        BigDecimal ytmDecimal = ytm.divide(HUNDRED);

        BondPrice price = priceCalculationService.calculatePrice(
                bond,
                asOf,
                ytmDecimal);

        return ResponseEntity.ok(
                new BondPriceResponse(
                        bond.getIsin(),
                        bond.getName(),
                        asOf,
                        ytm,
                        ytmDecimal,
                        price.price(),
                        price.dirtyPrice(),
                        price.accruedInterest(),
                        price.accruedInterestCharged(),
                        price.accruedDays(),
                        Instant.now()
                )
        );
    }
}
