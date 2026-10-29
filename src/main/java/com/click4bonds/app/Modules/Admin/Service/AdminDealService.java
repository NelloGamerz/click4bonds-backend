package com.click4bonds.app.Modules.Admin.Service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealAccrual;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealAccrualCalculator;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationService;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationSheetStrategyFactory;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationSheetValuesFactory;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminDealService {

    private final DealConfirmationService dealConfirmationService;
    private final DealConfirmationRepository dealConfirmationRepository;
    private final DealAccrualCalculator accrualCalculator;
    private final DealConfirmationSheetValuesFactory sheetValuesFactory;
    private final DocumentProperties documentProperties;

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
                .map(this::toSummary);
    }

    private AdminDealSummaryResponse toSummary(DealConfirmation deal) {

        return AdminDealSummaryResponse.from(
                deal,
                letterValues(deal),
                DealConfirmationSheetStrategyFactory.isSovereign(
                        deal.getBond() == null ? null : deal.getBond().getRating()));
    }

    /**
     * The figures this deal's confirmation letter prints, computed from the bond
     * as it stands now.
     *
     * <p>Best-effort, as on the create path: the factory refuses a deal with no
     * price or no accrued interest, and one such deal must not fail the whole
     * page. Null means the row carries no figures.</p>
     */
    private DealConfirmationSheetValues letterValues(DealConfirmation deal) {

        LocalDate valueDate = DealConfirmationDocumentData.dealDateOf(deal)
                .plusDays(documentProperties.getDeal().getValueDateOffsetDays());

        DealAccrual accrual = accrualCalculator
                .calculate(deal.getBond(), valueDate)
                .orElse(null);

        try {

            return sheetValuesFactory.build(DealConfirmationDocumentData.from(
                    deal, valueDate, accrual, null, null, null, null, null));

        } catch (RuntimeException cannotBePrinted) {

            log.warn(
                    "Letter values could not be computed for deal {}; the row carries none",
                    deal.getDealReference(),
                    cannotBePrinted);

            return null;
        }
    }
}
