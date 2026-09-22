package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;

/**
 * Entity to DTO mapping for {@link DealConfirmation}.
 *
 * <p>Shared by {@link DealConfirmationWriter} (which maps inside its
 * transaction, while the entity is managed) and {@link DealConfirmationService}
 * (which maps a replayed deal that was loaded with its associations fetched).
 * Both mappings read the bond, so both need those associations to be available
 * already.</p>
 */
@Component
public class DealConfirmationMapper {

    public DealConfirmationResponse toResponse(DealConfirmation deal) {

        return DealConfirmationResponse.builder()
                .dealConfirmationId(deal.getId())
                .dealReference(deal.getDealReference())
                .bondId(deal.getBond() == null ? null : deal.getBond().getId())
                .bondName(deal.getBond() == null ? null : deal.getBond().getName())
                .isin(deal.getIsin())
                .quantityPerLot(deal.getQuantityPerLot())
                .numberOfLots(deal.getNumberOfLots())
                .totalQuantity(deal.getTotalQuantity())
                .pricePerUnit(deal.getPricePerUnit())
                .totalAmount(deal.getTotalAmount())
                .status(deal.getStatus())
                .createdAt(deal.getCreatedAt())
                .build();
    }

    public DealConfirmationDocumentData toDocumentData(DealConfirmation deal) {
        return DealConfirmationDocumentData.from(deal);
    }
}
