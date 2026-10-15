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

import com.click4bonds.app.Modules.Bond.Dto.BondCalculationResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Dto.BondPriceResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondYtmResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondCalculationService;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.BondYtmService;
import com.click4bonds.app.Modules.Bond.Service.PriceCalculationService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read access to a bond's yield to maturity, and to the price that yield implies.
 *
 * <p>
 * Kept apart from {@link BondController} because all three endpoints are about
 * the bond's price/yield pair rather than its descriptive fields.
 *
 * <p>
 * The yield endpoint reports the bond's quoted yield — the figure stored on the
 * bond for today, or one calculated for a date the caller names; the price
 * endpoint solves the opposite direction, for a yield the caller supplies. Both
 * rest on the same cash-flow schedule, so the two agree.
 *
 * <p>
 * The calculation endpoint is for a caller that does not know which of the two it
 * wants: the bond's own {@code calculationType} decides, so one request serves a
 * bond created from a yield and a bond created from a price, and the response
 * says which it answered for.
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
    private final BondYtmService bondYtmService;
    private final BondCalculationService bondCalculationService;

    /**
     * Reports the bond's annual yield to maturity as of a date.
     *
     * <p>
     * Today's yield — the default, and the platform's quoted figure — is the one
     * stored on the bond, calculated on demand if the bond has never had one. A
     * yield for any other date is worked out for that date alone and is
     * deliberately not stored, so asking what a bond would have yielded cannot
     * move the figure it yields today. Which of the two a request is, and whether
     * anything is written, is {@link BondYtmService}'s to decide; this method
     * only routes and returns.
     *
     * @param isin            the bond's ISIN, case-insensitive.
     * @param calculationDate ISO date the yield and the accrual are measured to;
     *                        defaults to today, which is the usual settlement
     *                        horizon.
     * @return 404 when no bond carries that ISIN.
     */
    @GetMapping("/{isin}/ytm")
    public ResponseEntity<BondYtmResponse> getBondYtm(
            @PathVariable String isin,
            @RequestParam(required = false) LocalDate calculationDate) {

        return ResponseEntity.ok(
                bondYtmService.getYtm(isin, calculationDate));
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

    /**
     * Reports the bond's price or its yield, whichever its own calculation type
     * says was not the figure it was created from.
     *
     * <p>
     * A bond created from a yield is missing its price, so the price is derived
     * and returned; a bond created from a price is the other way round. The caller
     * does not choose — the bond does — and the response names the type it
     * answered for, so the two figures can be read knowing which of them was
     * given. This is the endpoint to call when the caller has an ISIN and no view
     * on which half of the pair it is after; {@link #getBondYtm} and
     * {@link #getBondPrice} remain for a caller that does know.
     *
     * <p>
     * Like the price lookup and unlike the yield lookup, this writes nothing: the
     * derived figure is computed for the request and discarded, so a read can
     * never move the bond's stored price or yield. Both directions run the engines
     * the other endpoints run, so the figures returned agree with them.
     *
     * @param isin            the bond's ISIN, case-insensitive.
     * @param calculationDate ISO settlement date the derived figure is as of;
     *                        defaults to today.
     * @return 400 when the bond declares no calculation type and holds neither
     *         figure to infer one from, or when the type it declares names a
     *         source figure it does not hold; 404 when no bond carries that ISIN;
     *         409 when a price has to be derived and the bond has no future cash
     *         flow to price.
     */
    @GetMapping("/{isin}/calculate")
    public ResponseEntity<BondCalculationResponse> calculateBond(
            @PathVariable String isin,
            @RequestParam(required = false) LocalDate calculationDate) {

        return ResponseEntity.ok(
                bondCalculationService.calculate(isin, calculationDate));
    }
}
