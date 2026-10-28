package com.click4bonds.app.Modules.Admin.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;

/**
 * One row of the employee's own deals listing.
 *
 * <p>Reads the bond and client, so both must already be fetched — the repository
 * query graph does that.</p>
 */
public record AdminDealSummaryResponse(
        UUID id,
        String dealReference,
        String clientName,
        String bondName,
        String isin,
        Long numberOfLots,
        Long totalQuantity,
        BigDecimal pricePerUnit,
        BigDecimal totalAmount,
        DealConfirmationStatus status,
        LocalDate dealDate,
        Instant createdAt) {

    public static AdminDealSummaryResponse from(DealConfirmation deal) {

        return new AdminDealSummaryResponse(
                deal.getId(),
                deal.getDealReference(),
                deal.getClient() == null ? null : deal.getClient().getName(),
                deal.getBond() == null ? null : deal.getBond().getName(),
                deal.getIsin(),
                deal.getNumberOfLots(),
                deal.getTotalQuantity(),
                deal.getPricePerUnit(),
                deal.getTotalAmount(),
                deal.getStatus(),
                deal.getDealDate(),
                deal.getCreatedAt());
    }
}
