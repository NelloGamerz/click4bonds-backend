package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminDealService {

    private final DealConfirmationService dealConfirmationService;
    private final DealConfirmationRepository dealConfirmationRepository;

    public DealConfirmationResponse create(
            CreateClientDealRequest request,
            UUID employeeId) {

        return dealConfirmationService
                .createDealForClient(
                        request.clientId(),
                        request.bondId(),
                        request.numberOfLots(),
                        request.dealDate(),
                        request.sendEmail(),
                        employeeId)
                .response();
    }

    /**
     * One page of the deals the given employee created, newest first unless the
     * caller asked otherwise. {@code search} is matched against the reference,
     * client, bond and ISIN; null or blank means no filter.
     */
    @Transactional(readOnly = true)
    public Page<AdminDealSummaryResponse> getDealsCreatedBy(
            UUID employeeId,
            String search,
            Pageable pageable) {

        String term = search == null || search.isBlank() ? null : search.trim();

        return dealConfirmationRepository
                .findCreatedBy(employeeId, term, pageable)
                .map(AdminDealSummaryResponse::from);
    }
}
