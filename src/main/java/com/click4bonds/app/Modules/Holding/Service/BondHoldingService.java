package com.click4bonds.app.Modules.Holding.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Page<BondHolding> getMyHoldings(
            String customerId,
            Pageable pageable) {

        return holdingRepository.findByCustomer_ClerkUserId(
                customerId,
                pageable);
    }

    @Transactional(readOnly = true)
    public BondHolding getHolding(
            String customerId,
            UUID holdingId) {

        BondHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Holding not found"));

        if (!holding.getCustomer().getClerkUserId().equals(customerId)) {
            throw new ForbiddenException(
                    "You cannot access this holding");
        }

        return holding;
    }
}
