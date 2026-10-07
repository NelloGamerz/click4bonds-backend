package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YtmCalculationServiceImpl implements YtmCalculationService {

    /** The rating that means "government security". */
    private static final String SOVEREIGN_RATING = "Sovereign";

    private final BondCashFlowService bondCashFlowService;
    private final XirrCalculator xirrCalculator;
    private final BondRepository bondRepository;

    @Override
    @Transactional
    public BigDecimal calculateYtm(Bond bond) {
        // Backward-compatible entry point: use the system date.
        return calculateYtm(bond, LocalDate.now());
    }

    @Override
    @Transactional
    public BigDecimal calculateYtm(Bond bond, LocalDate calculationDate) {
        validateBond(bond);

        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }

        // Step 1: Generate cash flows using BondCashFlowService
        List<XirrCalculator.CashFlow> cashFlows = bondCashFlowService.generateCashFlows(bond, calculationDate);

        // Step 2: Calculate XIRR (returns decimal format like 0.106947)
        // A Sovereign is quoted on a 30/360 year; every other bond keeps the
        // platform's actual/365 basis exactly as before.
        BigDecimal annualYtmDecimal = isSovereign(bond.getRating())
                ? xirrCalculator.calculate(cashFlows, XirrCalculator.DayCountBasis.THIRTY_360)
                : xirrCalculator.calculate(cashFlows);

        // Step 3: Convert from decimal to percentage and round to 4 decimal places
        // Example: 0.106947 -> 10.6947
        //
        // The rate itself already carries six decimals, so multiplying by 100
        // lands exactly on four: this step arranges the decimal point rather than
        // discarding anything. Four is where the precision stops being meaningful
        // — a basis point is the fourth place — and the two-decimal figure this
        // used to store was throwing away real differences between bonds that
        // quote to a hundredth of a percent.
        BigDecimal annualYtmPercentage = annualYtmDecimal
        .multiply(new BigDecimal("100"))
        .setScale(4, RoundingMode.HALF_UP);

        // BigDecimal annualYtmPercentage = annualYtmDecimal
        //         .multiply(new BigDecimal("100"))
        //         .divide(new BigDecimal("0.10"), 0, RoundingMode.HALF_UP)
        //         .multiply(new BigDecimal("0.10"))
        //         .setScale(2, RoundingMode.HALF_UP);

        // Step 4: Update Bond with calculated YTM
        bond.setAnnualYtm(annualYtmPercentage);
        bond.setYtmCalculatedAt(Instant.now());

        // Step 5: Persist the Bond
        bondRepository.save(bond);

        // Step 6: Return the YTM in decimal format (as per service contract)
        return annualYtmDecimal;
    }

    /**
     * Whether a bond's rating means a government security.
     *
     * <p>{@code Bond.rating} is free text, so the whole trimmed value is matched
     * case-insensitively: {@code "Sovereign"} and {@code "SOVEREIGN"} are one,
     * while {@code "Sovereign GOLD"} and {@code "AAA"} are not. Null is not a
     * Sovereign either — an unrated bond keeps the 365-day year it has always
     * been quoted on, which is the safe way round. The same rule decides which
     * deal-confirmation sheet a bond is lettered on (see
     * {@code DealConfirmationSheetStrategyFactory}).</p>
     *
     * @param rating the bond's rating, or null when it has none
     */
    private static boolean isSovereign(String rating) {
        return rating != null && rating.trim().equalsIgnoreCase(SOVEREIGN_RATING);
    }

    /**
     * Validates that the Bond is not null.
     * Delegates cash-flow validation to BondCashFlowService.
     *
     * @param bond the bond to validate
     * @throws IllegalArgumentException if bond is null
     */
    private void validateBond(Bond bond) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
    }
}
