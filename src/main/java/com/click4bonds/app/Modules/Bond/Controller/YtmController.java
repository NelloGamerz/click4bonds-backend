package com.click4bonds.app.Modules.Bond.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondYtmResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.YtmCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read access to a bond's yield to maturity.
 *
 * <p>
 * Kept apart from {@link BondController} because a YTM lookup is a computation
 * over the bond's cash flow rather than a read of the bond's own fields, and it
 * has the side effect of caching the result on the bond.
 */
@RestController
@RequestMapping("/api/bonds")
@RequiredArgsConstructor
@Slf4j
public class YtmController {

    private final BondService bondService;
    private final YtmCalculationService ytmCalculationService;

    /**
     * Calculates the annual yield to maturity for the bond with this ISIN.
     *
     * <p>
     * The result is cached on the bond ({@code annualYtm} and
     * {@code ytmCalculatedAt}) as a side effect of the calculation, so this is
     * not a purely read-only endpoint.
     *
     * @param isin            the bond's ISIN, case-insensitive.
     * @param calculationDate ISO date the projection starts from; defaults to
     *                        today, which is the usual settlement horizon.
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

        BigDecimal ytmDecimal = ytmCalculationService.calculateYtm(bond, asOf);

        /*
         * Read the percentage back off the bond rather than recomputing it, so
         * the response can never disagree with the value just persisted.
         */
        return ResponseEntity.ok(
                new BondYtmResponse(
                        bond.getIsin(),
                        bond.getName(),
                        asOf,
                        bond.getAnnualYtm(),
                        ytmDecimal,
                        bond.getPrice(),
                        bond.getYtmCalculatedAt()
                )
        );
    }
}
