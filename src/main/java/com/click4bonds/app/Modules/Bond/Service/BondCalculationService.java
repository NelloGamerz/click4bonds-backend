package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.AccruedInterest;
import com.click4bonds.app.Modules.Bond.Dto.BondCalculationResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Enums.CalculationType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers "what is this bond's other figure?" — the price if the bond was created
 * from a yield, the yield if it was created from a price.
 *
 * <p>
 * {@link BondYtmService} always reports a yield and the price endpoint always
 * takes one, which suits a caller who already knows which half it wants. This
 * serves the caller who does not: the bond's own {@link CalculationType} decides,
 * so the same request works for both kinds of bond and the response says which
 * kind it answered for.
 *
 * <p>
 * Nothing is written. The derivation here repeats work the platform already did
 * when the bond was created, priced or updated — {@link BondValuationService}
 * stores it there — and repeating it on a read must not move the stored figure.
 * That is why the yield direction goes through
 * {@link YtmCalculationService#previewAnnualYtm}, the non-persisting twin of
 * {@code calculateYtm}, while the price direction already persists nothing.
 *
 * <p>
 * The two directions are the two engines the rest of the platform uses, so a
 * figure from here is the one the equivalent lookup endpoint would report rather
 * than a second implementation that could drift.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BondCalculationService {

    /** Both figures are quoted in percent; the engines work in decimals. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BondService bondService;
    private final YtmCalculationService ytmCalculationService;
    private final PriceCalculationService priceCalculationService;
    private final AccruedInterestService accruedInterestService;

    /**
     * The bond's derived figure, with the stored one it came from and the accrual
     * at the same date.
     *
     * @param isin            the bond's ISIN, case-insensitive
     * @param calculationDate the settlement date the derived figure is as of, or
     *                        null for today
     * @return both readings of the pair, with the type that decided which was
     *         derived
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException
     *         when no bond carries that ISIN
     * @throws BadRequestException when the bond declares no calculation type and
     *                             holds neither a price nor a yield to infer one
     *                             from, or when the type it declares names a
     *                             source figure the bond does not hold
     */
    public BondCalculationResponse calculate(String isin, LocalDate calculationDate) {

        LocalDate asOf = calculationDate != null
                ? calculationDate
                : LocalDate.now();

        Bond bond = bondService.findBond(isin);

        /*
         * Resolved through the same method the create and update paths use, so a
         * bond that declared nothing is read the same way here as the way its
         * figures were arrived at then.
         */
        CalculationType calculationType = BondValuationService.resolveCalculationType(bond);

        if (calculationType == null) {
            throw new BadRequestException(
                    "Bond has no calculationType and holds neither a price nor a yield"
                            + " to infer one from: isin=" + bond.getIsin());
        }

        log.info(
                "Resolving bond calculation: isin={} calculationType={} calculationDate={}",
                bond.getIsin(),
                calculationType,
                asOf
        );

        /*
         * The whole point of the endpoint: the bond, not the caller, says which
         * half is the one to work out. The switch is exhaustive over the enum, so
         * a new calculation type cannot be added without this failing to compile.
         */
        Derived derived = switch (calculationType) {
            case BY_YTM -> priceFromStoredYield(bond, asOf);
            case BY_PRICE -> yieldFromStoredPrice(bond, asOf);
        };

        /*
         * Measured at the same date and on the same schedule as the derived
         * figure, so the two describe one settlement rather than two.
         */
        AccruedInterest accruedInterest = accruedInterestService.accrue(bond, asOf);

        return new BondCalculationResponse(
                bond.getIsin(),
                bond.getName(),
                asOf,
                calculationType,
                derived.ytmPercentage(),
                derived.ytmPercentage().divide(HUNDRED),
                derived.price(),
                accruedInterest.days(),
                accruedInterest.amount(),
                derived.calculatedAt()
        );
    }

    /**
     * The price that the bond's stored yield implies at this date.
     *
     * <p>
     * The yield is reported back as it is stored — it is the input, and under
     * {@code BY_YTM} it does not belong to the date the caller asked about, so
     * there is nothing to recalculate. Only the price moves with the date.
     */
    private Derived priceFromStoredYield(Bond bond, LocalDate asOf) {

        BigDecimal ytmPercentage = bond.getAnnualYtm();

        if (ytmPercentage == null) {
            throw new BadRequestException(
                    "Bond's calculationType is " + CalculationType.BY_YTM
                            + " but no yield is stored: isin=" + bond.getIsin());
        }

        /*
         * The engine works in decimals; the stored figure is the percentage a
         * caller reads off a screen. Dividing by 100 is exact.
         */
        BondPrice price = priceCalculationService.calculatePrice(
                bond,
                asOf,
                ytmPercentage.divide(HUNDRED));

        /*
         * The yield reported is the stored one, so its timestamp describes the
         * figure beside it and is honest to pass on.
         */
        return new Derived(ytmPercentage, price.price(), bond.getYtmCalculatedAt());
    }

    /**
     * The yield the bond's stored price implies at this date.
     *
     * <p>
     * The derived yield is deliberately not stored, and so carries no timestamp:
     * the bond already has a stored yield, computed when it was created or priced,
     * and reporting that one's timestamp beside this one would date the wrong
     * number. Under {@code BY_PRICE} the price is what the bond holds and what is
     * reported back unchanged.
     */
    private Derived yieldFromStoredPrice(Bond bond, LocalDate asOf) {

        BigDecimal price = bond.getPrice();

        if (price == null) {
            throw new BadRequestException(
                    "Bond's calculationType is " + CalculationType.BY_PRICE
                            + " but no price is stored: isin=" + bond.getIsin());
        }

        return new Derived(
                ytmCalculationService.previewAnnualYtm(bond, asOf),
                price,
                null);
    }

    /**
     * The three figures a direction produces, before the accrual and the bond's
     * descriptive fields are added around them.
     *
     * <p>
     * A carrier rather than three variables assigned inside a switch, so both
     * branches are exhaustive by construction and neither depends on the other
     * having run.
     *
     * @param ytmPercentage annual YTM as a percentage; its decimal form is derived
     *                      from it rather than carried, since the two are one
     *                      number written two ways
     * @param price         clean price as a percentage of par
     * @param calculatedAt  when the reported yield was persisted, or null when it
     *                      was derived for this response and not stored
     */
    private record Derived(
            BigDecimal ytmPercentage,
            BigDecimal price,
            Instant calculatedAt
    ) {
    }
}
