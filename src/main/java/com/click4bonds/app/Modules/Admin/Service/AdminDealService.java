package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
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

        /*
         * A settlement number identifies the clearing house's settlement, so a
         * letter for ICCL or NSCCL without one would be incomplete. DEMAT has no
         * such number.
         */
        if (request.modeOfDelivery() != DeliveryMode.DEMAT
                && (request.settlementNumber() == null
                        || request.settlementNumber().isBlank())) {

            throw new BadRequestException(
                    "Settlement number is required for " + request.modeOfDelivery());
        }

        /*
         * A demat purchase prints our DP particulars, so which depository they
         * belong to has to be chosen; a demat sale prints our bank particulars
         * and needs none.
         */
        if (request.side() == DealSide.BUY
                && request.modeOfDelivery() == DeliveryMode.DEMAT
                && request.depository() == null) {

            throw new BadRequestException(
                    "Depository is required for a DEMAT buy");
        }

        return dealConfirmationService
                .createDealForClient(
                        request.clientId(),
                        request.bondId(),
                        request.numberOfLots(),
                        request.dealDate(),
                        request.sendEmail(),
                        employeeId,
                        request.bankAccountId(),
                        request.dematAccountId(),
                        request.modeOfDelivery(),
                        request.settlementNumber(),
                        request.side(),
                        request.depository())
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
