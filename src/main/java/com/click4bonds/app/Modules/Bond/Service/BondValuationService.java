package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Enums.CalculationType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Works out the half of the price/yield pair that a bond's own
 * {@link CalculationType} says was not supplied.
 *
 * <p>
 * A price and a yield are two readings of one bond, so a bond created with one
 * of them should not be stored with the other left blank or left as an
 * unverified figure the caller happened to send. This service closes that gap
 * using the platform's two calculation engines — {@link YtmCalculationService}
 * for the price-to-yield direction and {@link PriceCalculationService} for
 * yield-to-price — so a derived value comes from the same arithmetic as the
 * equivalent lookup endpoint.
 *
 * <p>
 * It is deliberately separate from {@link BondService}: the service stays a thin
 * orchestrator, and the derivation can be exercised on its own against the real
 * engines.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BondValuationService {

    /**
     * Yields are stored as percentages; the engines work in decimals.
     */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final YtmCalculationService ytmCalculationService;
    private final PriceCalculationService priceCalculationService;

    /**
     * Fills in the derived half of the pair, in place.
     *
     * <p>
     * A null calculation type is left alone: the caller declared nothing, so both
     * figures are kept exactly as sent.
     *
     * @param bond            the bond, already populated from the request
     * @param calculationDate the settlement date the derivation is as of
     * @throws BadRequestException when the declared input was not supplied
     */
    public void applyCalculationType(Bond bond, LocalDate calculationDate) {

        CalculationType calculationType = bond.getCalculationType();

        if (calculationType == null) {
            if (bond.getPrice() != null) {
                calculationType = CalculationType.BY_PRICE;
            } else if (bond.getAnnualYtm() != null) {
                calculationType = CalculationType.BY_YTM;
            } else {
                return;
            }
        }

        switch (calculationType) {
            case BY_PRICE -> deriveYield(bond, calculationDate);
            case BY_YTM -> derivePrice(bond, calculationDate);
        }
    }

    /**
     * Calculates the yield from the price and stores it on {@code annualYtm}.
     *
     * <p>
     * Delegated rather than reimplemented, which is also what makes the derived
     * figure agree with {@code GET /api/bonds/{isin}/ytm}: that endpoint calls the
     * same method. It persists the bond as its own side effect, inside this
     * create's transaction.
     */
    private void deriveYield(Bond bond, LocalDate calculationDate) {

        if (bond.getPrice() == null) {
            throw new BadRequestException(
                    "price is required when calculationType is " + CalculationType.BY_PRICE);
        }

        ytmCalculationService.calculateYtm(bond, calculationDate);

        log.info(
                "Derived yield from price: isin={} price={} annualYtm={}%",
                bond.getIsin(),
                bond.getPrice(),
                bond.getAnnualYtm());
    }

    /**
     * Calculates the price from the yield and stores it on {@code price}.
     *
     * <p>
     * Nothing is persisted here; the create that called this saves the bond.
     */
    private void derivePrice(Bond bond, LocalDate calculationDate) {

        if (bond.getAnnualYtm() == null) {
            throw new BadRequestException(
                    "annualYtm is required when calculationType is " + CalculationType.BY_YTM);
        }

        BondPrice price = priceCalculationService.calculatePrice(
                bond,
                calculationDate,
                bond.getAnnualYtm().divide(HUNDRED));

        bond.setPrice(price.price());

        log.info(
                "Derived price from yield: isin={} annualYtm={}% price={}",
                bond.getIsin(),
                bond.getAnnualYtm(),
                price.price());
    }
}
