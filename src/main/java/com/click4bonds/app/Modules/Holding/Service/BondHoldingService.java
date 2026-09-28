package com.click4bonds.app.Modules.Holding.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Holding.Model.BondHolding;
import com.click4bonds.app.Modules.Holding.Repository.BondHoldingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BondHoldingService {

    private final BondHoldingRepository holdingRepository;
    private final AnalyticsService analyticsService;

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
