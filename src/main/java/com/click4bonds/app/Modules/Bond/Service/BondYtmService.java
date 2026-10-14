package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.AccruedInterest;
import com.click4bonds.app.Modules.Bond.Dto.BondYtmResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers "what does this bond yield as of this date?".
 *
 * <p>
 * A yield belongs to a date, and the two dates a caller can ask about are not
 * the same question. Today's yield is the platform's quoted figure: it is stored
 * on the bond and is worth keeping, so it is calculated once and read thereafter.
 * Any other date is a one-off — what the bond would have yielded, or would yield
 * — and the answer must not disturb the quoted one. This service draws that line
 * in one place:
 *
 * <ul>
 * <li>today, with a stored yield — report the stored figure, calculate nothing;
 * <li>today, with no stored yield — calculate it, store it, report it;
 * <li>any other date — calculate it, report it, store nothing.
 * </ul>
 *
 * <p>
 * Nothing is solved for here. The arithmetic is {@link YtmCalculationService}
 * and the accrual is {@link AccruedInterestService}; what this class contributes
 * is the decision of which of the three cases a request falls into, and the
 * assembling of the response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BondYtmService {

    /** A yield is quoted in percent; the engine works in decimals. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BondService bondService;
    private final YtmCalculationService ytmCalculationService;
    private final AccruedInterestService accruedInterestService;

    /**
     * The bond's yield as of the requested date, with the accrual it implies.
     *
     * <p>
     * A request that names no date is a request about today, which is the usual
     * settlement horizon and the only date whose yield the platform keeps.
     *
     * <p>
     * The accrued interest is measured at the same date and on the same schedule
     * as the yield, so the two figures reported together describe one settlement
     * rather than two.
     *
     * @param isin            the bond's ISIN, case-insensitive
     * @param calculationDate the date the yield is as of, or null for today
     * @return the bond's yield, its accrual and the date both are measured at
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException
     *         when no bond carries that ISIN
     */
    @Transactional
    public BondYtmResponse getYtm(String isin, LocalDate calculationDate) {

        /*
         * Read once, so a request that arrives either side of midnight cannot be
         * classified against one date and calculated against another.
         */
        LocalDate today = LocalDate.now();

        LocalDate asOf = calculationDate != null
                ? calculationDate
                : today;

        boolean asOfToday = asOf.equals(today);

        log.info(
                "Resolving YTM: isin={} calculationDate={} asOfToday={}",
                isin,
                asOf,
                asOfToday
        );

        Bond bond = bondService.findBond(isin);

        BigDecimal ytmPercentage = asOfToday
                ? storedOrCalculated(bond, isin, asOf)
                : ytmCalculationService.previewAnnualYtm(bond, asOf);

        /*
         * When the yield was calculated for a date other than today it was not
         * persisted, so the bond's timestamp describes a different figure and
         * reporting it beside this one would date the wrong number. Null is the
         * honest answer for a yield that was never stored.
         */
        Instant calculatedAt = asOfToday
                ? bond.getYtmCalculatedAt()
                : null;

        /*
         * Both representations are read off the one figure. Moving the decimal
         * point is a change of representation, not a second calculation, so the
         * two can never disagree.
         */
        BigDecimal ytmDecimal = ytmPercentage == null
                ? null
                : ytmPercentage.divide(HUNDRED);

        /*
         * The accrual is the one figure here with no column behind it: it is a
         * function of the date this call chose rather than read.
         */
        AccruedInterest accruedInterest = accruedInterestService.accrue(
                bond,
                asOf);

        return new BondYtmResponse(
                bond.getIsin(),
                bond.getName(),
                asOf,
                ytmPercentage,
                ytmDecimal,
                bond.getPrice(),
                accruedInterest.days(),
                accruedInterest.amount(),
                calculatedAt
        );
    }

    /**
     * Today's yield: the stored figure, or a freshly calculated and persisted
     * one when the bond has never had a yield worked out.
     *
     * <p>
     * A stored yield is returned untouched rather than recalculated. It is the
     * platform's quoted figure, settled when the bond was created, priced or
     * updated, and re-deriving it on a read would let a mere lookup move it.
     *
     * <p>
     * A bond arriving here with no yield is one whose calculation never ran — an
     * import that carried a price but no yield, or a bond whose yield was
     * invalidated by a price change. Calculating it closes that gap, and storing
     * it means the work is done once rather than on every read.
     */
    private BigDecimal storedOrCalculated(Bond bond, String isin, LocalDate asOf) {

        BigDecimal stored = bond.getAnnualYtm();

        if (stored != null) {
            return stored;
        }

        log.info(
                "No stored YTM; calculating and persisting: isin={} calculationDate={}",
                isin,
                asOf
        );

        /*
         * Evicted before the write, as everywhere else a bond is written: a
         * failed eviction leaves the cache and the database in agreement, since
         * the surrounding transaction rolls back and the stale entry still
         * describes the row that survived.
         */
        bondService.evictCachedBond(isin);

        ytmCalculationService.calculateYtm(bond, asOf);

        return bond.getAnnualYtm();
    }
}
