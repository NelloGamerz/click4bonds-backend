package com.click4bonds.app.Modules.Holding.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Holding.Model.BondHolding;
import com.click4bonds.app.Modules.Holding.Repository.BondHoldingRepository;
import com.click4bonds.app.Modules.User.Model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BondHoldingService {

    private final BondHoldingRepository holdingRepository;
    private final AnalyticsService analyticsService;

    /**
     * Adds a completed purchase to the customer's holding in a bond.
     *
     * <p>One row per (customer, bond): a first purchase creates it, and every
     * later one adds to it. The average price is recomputed as a weighted
     * average of what was already held and what has just been bought, so the
     * figure stays the average cost of the units actually owned rather than
     * drifting to the most recent price.</p>
     *
     * <p>Expected to run inside the caller's transaction — the checkout that
     * pays for an order calls it so that the holding moves with the inventory
     * and the deal, and rolls back with them.</p>
     *
     * @param customer       the buyer
     * @param bond           the bond bought
     * @param quantity       units bought; must be greater than zero
     * @param pricePerUnit   clean price paid per unit
     * @return the created or updated holding
     * @throws BadRequestException when the quantity is not positive or the price
     *                             is missing. Neither is a state to guess at: a
     *                             zero price would understate the holding and a
     *                             non-positive quantity would silently reduce it.
     */
    public BondHolding recordPurchase(
            User customer,
            Bond bond,
            long quantity,
            BigDecimal pricePerUnit) {

        if (quantity <= 0) {
            throw new BadRequestException(
                    "Purchased quantity must be greater than 0 (was " + quantity + ")");
        }

        if (pricePerUnit == null) {
            throw new BadRequestException(
                    "Cannot record a purchase with no price for bond: " + bond.getIsin());
        }

        BondHolding holding = holdingRepository
                .findByCustomerIdAndBondId(customer.getId(), bond.getId())
                .orElse(null);

        if (holding == null) {

            holding = BondHolding.builder()
                    .customer(customer)
                    .bond(bond)
                    .quantity(quantity)
                    .averagePurchasePrice(pricePerUnit)
                    .build();

        } else {

            long newQuantity = Math.addExact(holding.getQuantity(), quantity);

            BigDecimal existingCost = holding.getAveragePurchasePrice()
                    .multiply(BigDecimal.valueOf(holding.getQuantity()));

            BigDecimal addedCost = pricePerUnit
                    .multiply(BigDecimal.valueOf(quantity));

            /*
             * Scale 4 to match the column. newQuantity is at least the units just
             * bought and those were validated positive, so it is never zero.
             */
            BigDecimal newAverage = existingCost.add(addedCost)
                    .divide(
                            BigDecimal.valueOf(newQuantity),
                            4,
                            RoundingMode.HALF_UP);

            holding.setQuantity(newQuantity);
            holding.setAveragePurchasePrice(newAverage);
        }

        /*
         * No analytics event is emitted here. The catalogue has no "holding
         * changed" event, and reusing PORTFOLIO_HOLDING_VIEW — which means the
         * customer looked at a holding — would record a purchase as a page view.
         * The order and payment events are tracked by the checkout instead.
         */
        return holdingRepository.save(holding);
    }

    @Transactional(readOnly = true)
    public Page<BondHolding> getMyHoldings(
            UUID customerId,
            Pageable pageable) {

        Page<BondHolding> holdings = holdingRepository.findByCustomer_Id(
                customerId,
                pageable);
        analyticsService.track(
                AnalyticsEventType.PORTFOLIO_HOLDING_VIEW,
                customerId,
                null,
                null,
                "WEB",
                "PORTFOLIO",
                java.util.Map.of(
                        "resultCount", holdings.getNumberOfElements(),
                        "page", holdings.getNumber()));
        return holdings;
    }

    @Transactional(readOnly = true)
    public BondHolding getHolding(
            UUID customerId,
            UUID holdingId) {

        BondHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Holding not found"));

        if (!holding.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException(
                    "You cannot access this holding");
        }

        analyticsService.track(
                AnalyticsEventType.PORTFOLIO_HOLDING_VIEW,
                customerId,
                null,
                holding.getBond().getId(),
                "WEB",
                "PORTFOLIO",
                java.util.Map.of("holdingId", holding.getId().toString()));
        return holding;
    }
}
