package com.click4bonds.app.Modules.Admin.Service;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDealService {

    private final DealConfirmationService dealConfirmationService;

    public DealConfirmationResponse create(CreateClientDealRequest request) {

        return dealConfirmationService
                .createDealForClient(
                        request.clientId(),
                        request.bondId(),
                        request.numberOfLots(),
                        request.dealDate(),
                        request.sendEmail())
                .response();
    }
}
