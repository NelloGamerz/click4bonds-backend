package com.click4bonds.app.Modules.Bond.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondPriceUpdateRequest;
import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

// @Service
// @RequiredArgsConstructor
// @Transactional
// public class BondService {

//         private final BondRepository bondRepository;
//         private final UserRepository userRepository;

//         public BondResponse createBond(
//                         CreateBondRequest request,
//                         String adminId) throws BadRequestException {

//                 if (bondRepository.existsByIsin(request.getIsin())) {
//                         throw new ConflictException(
//                                         "Bond with ISIN already exists");
//                 }

//                 if (!request.getMaturityDate().isAfter(request.getIssueDate())) {
//                         throw new BadRequestException(
//                                         "Maturity date must be after issue date");
//                 }

//                 User admin = userRepository.findByClerkUserId(adminId)
//                                 .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

//                 Bond bond = Bond.builder()
//                                 .isin(request.getIsin())
//                                 .name(request.getName())
//                                 .issuer(request.getIssuer())
//                                 .description(request.getDescription())
//                                 .faceValue(request.getFaceValue())
//                                 .couponRate(request.getCouponRate())
//                                 .couponFrequency(request.getCouponFrequency())
//                                 .issueDate(request.getIssueDate())
//                                 .maturityDate(request.getMaturityDate())
//                                 .sellingPrice(request.getSellingPrice())
//                                 .minimumInvestment(request.getMinimumInvestment())
//                                 .totalUnits(request.getTotalUnits())
//                                 .availableUnits(request.getTotalUnits())
//                                 .status(BondStatus.DRAFT)
//                                 .createdBy(admin)
//                                 .build();

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         @Transactional(readOnly = true)
//         public BondResponse getBond(UUID id) {

//                 Bond bond = bondRepository.findById(id)
//                                 .orElseThrow(() -> new ResourceNotFoundException(
//                                                 "Bond not found: " + id));

//                 return mapToResponse(bond);
//         }

//         @Transactional(readOnly = true)
//         public Page<BondResponse> getBonds(Pageable pageable) {

//                 return bondRepository.findAll(pageable)
//                                 .map(this::mapToResponse);
//         }

//         @Transactional(readOnly = true)
//         public Page<BondResponse> getActiveBonds(Pageable pageable) {

//                 return bondRepository
//                                 .findByStatus(BondStatus.ACTIVE, pageable)
//                                 .map(this::mapToResponse);
//         }

//         public BondResponse updateBond(
//                         UUID bondId,
//                         UpdateBondRequest request) throws BadRequestException {

//                 Bond bond = bondRepository.findById(bondId)
//                                 .orElseThrow(() -> new ResourceNotFoundException(
//                                                 "Bond not found: " + bondId));

//                 if (bond.getStatus() == BondStatus.MATURED) {
//                         throw new BadRequestException(
//                                         "Matured bond cannot be updated");
//                 }

//                 if (request.getName() != null) {
//                         bond.setName(request.getName());
//                 }

//                 if (request.getIssuer() != null) {
//                         bond.setIssuer(request.getIssuer());
//                 }

//                 if (request.getDescription() != null) {
//                         bond.setDescription(request.getDescription());
//                 }

//                 if (request.getFaceValue() != null) {
//                         bond.setFaceValue(request.getFaceValue());
//                 }

//                 if (request.getCouponRate() != null) {
//                         bond.setCouponRate(request.getCouponRate());
//                 }

//                 if (request.getCouponFrequency() != null) {
//                         bond.setCouponFrequency(request.getCouponFrequency());
//                 }

//                 if (request.getIssueDate() != null) {
//                         bond.setIssueDate(request.getIssueDate());
//                 }

//                 if (request.getMaturityDate() != null) {
//                         bond.setMaturityDate(request.getMaturityDate());
//                 }

//                 if (request.getSellingPrice() != null) {
//                         bond.setSellingPrice(request.getSellingPrice());
//                 }

//                 if (request.getMinimumInvestment() != null) {
//                         bond.setMinimumInvestment(request.getMinimumInvestment());
//                 }

//                 return mapToResponse(bondRepository.save(bond));
//         }

//         public BondResponse activateBond(UUID bondId) throws BadRequestException {

//                 Bond bond = getEntity(bondId);

//                 if (bond.getAvailableUnits() <= 0) {
//                         throw new BadRequestException(
//                                         "Cannot activate a sold-out bond");
//                 }

//                 bond.setStatus(BondStatus.ACTIVE);

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         public BondResponse suspendBond(UUID bondId) {

//                 Bond bond = getEntity(bondId);

//                 bond.setStatus(BondStatus.SUSPENDED);

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         public void cancelBond(UUID bondId) throws BadRequestException {

//                 Bond bond = getEntity(bondId);

//                 // if (bond.getAvailableUnits() != bond.getTotalUnits()) {
//                 // throw new BadRequestException(
//                 // "Bond with existing purchases cannot be deleted");
//                 // }
//                 if (!Objects.equals(bond.getAvailableUnits(), bond.getTotalUnits())) {
//                         throw new BadRequestException(
//                                         "Bond with existing purchases cannot be cancelled");
//                 }

//                 bond.setStatus(BondStatus.CANCELLED);

//                 bondRepository.save(bond);
//         }

//         private Bond getEntity(UUID id) {

//                 return bondRepository.findById(id)
//                                 .orElseThrow(() -> new ResourceNotFoundException(
//                                                 "Bond not found: " + id));
//         }

//         private BondResponse mapToResponse(Bond bond) {

//                 return BondResponse.builder()
//                                 .id(bond.getId())
//                                 .isin(bond.getIsin())
//                                 .name(bond.getName())
//                                 .issuer(bond.getIssuer())
//                                 .description(bond.getDescription())
//                                 .faceValue(bond.getFaceValue())
//                                 .couponRate(bond.getCouponRate())
//                                 .couponFrequency(bond.getCouponFrequency())
//                                 .issueDate(bond.getIssueDate())
//                                 .maturityDate(bond.getMaturityDate())
//                                 .sellingPrice(bond.getSellingPrice())
//                                 .minimumInvestment(bond.getMinimumInvestment())
//                                 .totalUnits(bond.getTotalUnits())
//                                 .availableUnits(bond.getAvailableUnits())
//                                 .status(bond.getStatus())
//                                 .createdAt(bond.getCreatedAt())
//                                 .updatedAt(bond.getUpdatedAt())
//                                 .build();
//         }

// }

// @Service
// @RequiredArgsConstructor
// @Transactional
// public class BondService {

//         private final BondRepository bondRepository;
//         private final UserRepository userRepository;

//         public BondResponse createBond(
//                         CreateBondRequest request,
//                         String adminId) throws BadRequestException {

//                 // Check duplicate ISIN
//                 if (bondRepository.existsByIsin(request.getIsin())) {
//                         throw new ConflictException(
//                                         "Bond with ISIN already exists");
//                 }

//                 // Validate maturity date
//                 if (request.getMaturityDate() == null) {
//                         throw new BadRequestException(
//                                         "Maturity date is required");
//                 }

//                 // Issue date is no longer part of the new model,
//                 // so there is no issue-date/maturity-date comparison here.

//                 // Find admin
//                 User admin = userRepository.findByClerkUserId(adminId)
//                                 .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

//                 Bond bond = Bond.builder()
//                                 .serialNumber(request.getSerialNumber())
//                                 .isin(request.getIsin())
//                                 .name(request.getName())
//                                 .couponRate(request.getCouponRate())
//                                 .securityType(request.getSecurityType())
//                                 .rating(request.getRating())
//                                 .ratingAgency(request.getRatingAgency())
//                                 .maturityDate(request.getMaturityDate())
//                                 .putDate(request.getPutDate())
//                                 .callDate(request.getCallDate())
//                                 .price(request.getPrice())
//                                 .semiYtm(request.getSemiYtm())
//                                 .annualYtm(request.getAnnualYtm())
//                                 .ytc(request.getYtc())
//                                 .ipDate(request.getIpDate())
//                                 .quantumInLacs(request.getQuantumInLacs())
//                                 .lotSize(request.getLotSize())
//                                 .status(BondStatus.DRAFT)
//                                 .createdBy(admin)
//                                 .build();

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         @Transactional(readOnly = true)
//         public BondResponse getBond(UUID id) {

//                 Bond bond = bondRepository.findById(id)
//                                 .orElseThrow(() -> new ResourceNotFoundException(
//                                                 "Bond not found: " + id));

//                 return mapToResponse(bond);
//         }

//         @Transactional(readOnly = true)
//         public Page<BondResponse> getBonds(Pageable pageable) {

//                 return bondRepository.findAll(pageable)
//                                 .map(this::mapToResponse);
//         }

//         @Transactional(readOnly = true)
//         public Page<BondResponse> getActiveBonds(Pageable pageable) {

//                 return bondRepository
//                                 .findByStatus(BondStatus.ACTIVE, pageable)
//                                 .map(this::mapToResponse);
//         }

//         public BondResponse updateBond(
//                         UUID bondId,
//                         UpdateBondRequest request) throws BadRequestException {

//                 Bond bond = bondRepository.findById(bondId)
//                                 .orElseThrow(() -> new ResourceNotFoundException(
//                                                 "Bond not found: " + bondId));

//                 if (bond.getStatus() == BondStatus.MATURED) {
//                         throw new BadRequestException(
//                                         "Matured bond cannot be updated");
//                 }

//                 if (request.getSerialNumber() != null) {
//                         bond.setSerialNumber(request.getSerialNumber());
//                 }

//                 if (request.getName() != null) {
//                         bond.setName(request.getName());
//                 }

//                 if (request.getCouponRate() != null) {
//                         bond.setCouponRate(request.getCouponRate());
//                 }

//                 if (request.getSecurityType() != null) {
//                         bond.setSecurityType(request.getSecurityType());
//                 }

//                 if (request.getRating() != null) {
//                         bond.setRating(request.getRating());
//                 }

//                 if (request.getRatingAgency() != null) {
//                         bond.setRatingAgency(request.getRatingAgency());
//                 }

//                 if (request.getMaturityDate() != null) {
//                         bond.setMaturityDate(request.getMaturityDate());
//                 }

//                 if (request.getPutDate() != null) {
//                         bond.setPutDate(request.getPutDate());
//                 }

//                 if (request.getCallDate() != null) {
//                         bond.setCallDate(request.getCallDate());
//                 }

//                 if (request.getPrice() != null) {
//                         bond.setPrice(request.getPrice());
//                 }

//                 if (request.getSemiYtm() != null) {
//                         bond.setSemiYtm(request.getSemiYtm());
//                 }

//                 if (request.getAnnualYtm() != null) {
//                         bond.setAnnualYtm(request.getAnnualYtm());
//                 }

//                 if (request.getYtc() != null) {
//                         bond.setYtc(request.getYtc());
//                 }

//                 if (request.getIpDate() != null) {
//                         bond.setIpDate(request.getIpDate());
//                 }

//                 if (request.getQuantumInLacs() != null) {
//                         bond.setQuantumInLacs(request.getQuantumInLacs());
//                 }

//                 if (request.getLotSize() != null) {
//                         bond.setLotSize(request.getLotSize());
//                 }

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         public BondResponse activateBond(UUID bondId)
//                         throws BadRequestException {

//                 Bond bond = getEntity(bondId);

//                 /*
//                  * The old availableUnits check has been removed because
//                  * totalUnits/availableUnits no longer exist in the new model.
//                  *
//                  * If you still need "sold out" functionality, we should add
//                  * a separate inventory/units model.
//                  */

//                 bond.setStatus(BondStatus.ACTIVE);

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         public BondResponse suspendBond(UUID bondId) {

//                 Bond bond = getEntity(bondId);

//                 bond.setStatus(BondStatus.SUSPENDED);

//                 return mapToResponse(
//                                 bondRepository.save(bond));
//         }

//         public void cancelBond(UUID bondId)
//                         throws BadRequestException {

//                 Bond bond = getEntity(bondId);

//                 /*
//                  * The old totalUnits/availableUnits validation has been removed.
//                  *
//                  * If cancellation must be prevented after purchases,
//                  * that check should now be based on your purchase/order table.
//                  */

//                 bond.setStatus(BondStatus.CANCELLED);

//                 bondRepository.save(bond);
//         }

//         private Bond getEntity(UUID id) {

//                 return bondRepository.findById(id)
//                                 .orElseThrow(() -> new ResourceNotFoundException(
//                                                 "Bond not found: " + id));
//         }

//         private BondResponse mapToResponse(Bond bond) {

//                 return BondResponse.builder()
//                                 .id(bond.getId())
//                                 .serialNumber(bond.getSerialNumber())
//                                 .isin(bond.getIsin())
//                                 .name(bond.getName())
//                                 .couponRate(bond.getCouponRate())
//                                 .securityType(bond.getSecurityType())
//                                 .rating(bond.getRating())
//                                 .ratingAgency(bond.getRatingAgency())
//                                 .maturityDate(bond.getMaturityDate())
//                                 .putDate(bond.getPutDate())
//                                 .callDate(bond.getCallDate())
//                                 .price(bond.getPrice())
//                                 .semiYtm(bond.getSemiYtm())
//                                 .annualYtm(bond.getAnnualYtm())
//                                 .ytc(bond.getYtc())
//                                 .ipDate(bond.getIpDate())
//                                 .quantumInLacs(bond.getQuantumInLacs())
//                                 .lotSize(bond.getLotSize())
//                                 .status(bond.getStatus())
//                                 .createdAt(bond.getCreatedAt())
//                                 .updatedAt(bond.getUpdatedAt())
//                                 .build();
//         }
// }

@Service
@RequiredArgsConstructor
@Transactional
public class BondService {

        private final BondRepository bondRepository;
        private final UserRepository userRepository;

        // =========================================================
        // CREATE
        // =========================================================

        public BondResponse createBond(
                        CreateBondRequest request,
                        String adminId) throws BadRequestException {

                // -----------------------------------------------------
                // Duplicate ISIN
                // -----------------------------------------------------

                String isin = request.getIsin().trim().toUpperCase();

                if (bondRepository.existsByIsin(isin)) {
                        throw new ConflictException(
                                        "Bond with ISIN already exists: " + isin);
                }

                // -----------------------------------------------------
                // Validate maturity
                // -----------------------------------------------------

                if (request.getMaturityType() == null) {
                        throw new BadRequestException(
                                        "Maturity type is required");
                }

                if (request.getMaturityType().name().equals("FIXED")
                                && request.getMaturityDate() == null) {

                        throw new BadRequestException(
                                        "Maturity date is required for fixed maturity bonds");
                }

                // -----------------------------------------------------
                // Find admin
                // -----------------------------------------------------

                User admin = userRepository.findByClerkUserId(adminId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Admin not found"));

                // -----------------------------------------------------
                // Build Bond
                // -----------------------------------------------------

                Bond bond = Bond.builder()

                                .serialNumber(request.getSerialNumber())

                                .name(request.getName().trim())

                                .isin(isin)

                                // Classification
                                .category(request.getCategory())
                                .securityType(request.getSecurityType())
                                .rating(request.getRating())
                                .ratingAgency(request.getRatingAgency())

                                // Coupon
                                .couponRate(request.getCouponRate())
                                .couponFrequency(request.getCouponFrequency())
                                .ipDateDescription(request.getIpDateDescription())

                                // Maturity
                                .maturityType(request.getMaturityType())
                                .maturityDate(request.getMaturityDate())
                                .maturityDescription(request.getMaturityDescription())

                                // Put / Call
                                .putCallDescription(request.getPutCallDescription())

                                // Market
                                .price(request.getPrice())

                                // IMPORTANT:
                                // YTM values are NOT accepted from CreateBondRequest.
                                // They will be calculated internally.
                                .semiYtm(null)
                                .annualYtm(null)
                                .ytc(null)
                                .ytmCalculatedAt(null)

                                // Quantum
                                .quantumDescription(request.getQuantumDescription())
                                .quantumInLacs(request.getQuantumInLacs())

                                // Lot
                                .lotSizeDescription(request.getLotSizeDescription())
                                .lotSize(request.getLotSize())
                                .lotSizeType(request.getLotSizeType())

                                // Status
                                .status(BondStatus.DRAFT)

                                // Audit
                                .createdBy(admin)

                                .build();

                Bond savedBond = bondRepository.save(bond);

                return mapToResponse(savedBond);
        }

        public List<BondResponse> updatePrices(
                        List<BondPriceUpdateRequest> requests)
                        throws BadRequestException {

                List<Bond> bonds = new java.util.ArrayList<>();

                for (BondPriceUpdateRequest request : requests) {
                        String isin = request.getIsin().trim().toUpperCase();
                        Bond bond = bondRepository.findByIsin(isin)
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "Bond not found with ISIN: " + isin));

                        if (bond.getStatus() == BondStatus.MATURED) {
                                throw new BadRequestException(
                                                "Matured bond cannot be updated: " + isin);
                        }

                        bond.setPrice(request.getPrice());
                        invalidateYieldCalculation(bond);
                        bonds.add(bond);
                }

                return bondRepository.saveAll(bonds).stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        // =========================================================
        // GET SINGLE BOND
        // =========================================================

        @Transactional(readOnly = true)
        public BondResponse getBond(String id) {

                Bond bond = getbondByIs(id);

                return mapToResponse(bond);
        }

        // =========================================================
        // GET ALL BONDS
        // =========================================================

        @Transactional(readOnly = true)
        public Page<BondResponse> getBonds(Pageable pageable) {

                return bondRepository
                                .findAll(pageable)
                                .map(this::mapToResponse);
        }

        @Transactional(readOnly = true)
        public Page<BondResponse> getBonds(
                        String search,
                        Pageable pageable) {

                if (search == null || search.isBlank()) {
                        return getBonds(pageable);
                }

                return bondRepository
                                .searchBonds(search, pageable)
                                .map(this::mapToResponse);
        }

        // =========================================================
        // GET ACTIVE BONDS
        // =========================================================

        @Transactional(readOnly = true)
        public Page<BondResponse> getActiveBonds(Pageable pageable) {

                return bondRepository
                                .findByStatus(
                                                BondStatus.ACTIVE,
                                                pageable)
                                .map(this::mapToResponse);
        }

        // =========================================================
        // UPDATE
        // =========================================================

        public BondResponse updateBond(
                        UUID bondId,
                        UpdateBondRequest request)
                        throws BadRequestException {

                Bond bond = getEntity(bondId);

                // -----------------------------------------------------
                // Matured bond cannot be modified
                // -----------------------------------------------------

                if (bond.getStatus() == BondStatus.MATURED) {

                        throw new BadRequestException(
                                        "Matured bond cannot be updated");
                }

                // -----------------------------------------------------
                // Basic information
                // -----------------------------------------------------

                if (request.getSerialNumber() != null) {
                        bond.setSerialNumber(
                                        request.getSerialNumber());
                }

                if (request.getName() != null) {
                        bond.setName(
                                        request.getName().trim());
                }

                // -----------------------------------------------------
                // Classification
                // -----------------------------------------------------

                if (request.getCategory() != null) {
                        bond.setCategory(
                                        request.getCategory());
                }

                if (request.getSecurityType() != null) {
                        bond.setSecurityType(
                                        request.getSecurityType());
                }

                if (request.getRating() != null) {
                        bond.setRating(
                                        request.getRating());
                }

                if (request.getRatingAgency() != null) {
                        bond.setRatingAgency(
                                        request.getRatingAgency());
                }

                // -----------------------------------------------------
                // Coupon
                // -----------------------------------------------------

                if (request.getCouponRate() != null) {
                        bond.setCouponRate(
                                        request.getCouponRate());
                }

                if (request.getCouponFrequency() != null) {
                        bond.setCouponFrequency(
                                        request.getCouponFrequency());
                }

                if (request.getIpDateDescription() != null) {
                        bond.setIpDateDescription(
                                        request.getIpDateDescription());
                }

                // -----------------------------------------------------
                // Maturity
                // -----------------------------------------------------

                if (request.getMaturityType() != null) {
                        bond.setMaturityType(
                                        request.getMaturityType());
                }

                if (request.getMaturityDate() != null) {
                        bond.setMaturityDate(
                                        request.getMaturityDate());
                }

                if (request.getMaturityDescription() != null) {
                        bond.setMaturityDescription(
                                        request.getMaturityDescription());
                }

                // -----------------------------------------------------
                // Put / Call
                // -----------------------------------------------------

                if (request.getPutCallDescription() != null) {
                        bond.setPutCallDescription(
                                        request.getPutCallDescription());
                }

                // -----------------------------------------------------
                // Price
                // -----------------------------------------------------

                if (request.getPrice() != null) {
                        bond.setPrice(
                                        request.getPrice());

                        /*
                         * Price changed.
                         *
                         * Previously calculated YTM is now potentially stale.
                         *
                         * Do not leave old confidential YTM values in the DB.
                         */
                        invalidateYieldCalculation(bond);
                }

                // -----------------------------------------------------
                // Quantum
                // -----------------------------------------------------

                if (request.getQuantumDescription() != null) {
                        bond.setQuantumDescription(
                                        request.getQuantumDescription());
                }

                if (request.getQuantumInLacs() != null) {
                        bond.setQuantumInLacs(
                                        request.getQuantumInLacs());
                }

                // -----------------------------------------------------
                // Lot Size
                // -----------------------------------------------------

                if (request.getLotSizeDescription() != null) {
                        bond.setLotSizeDescription(
                                        request.getLotSizeDescription());
                }

                if (request.getLotSize() != null) {
                        bond.setLotSize(
                                        request.getLotSize());
                }

                if (request.getLotSizeType() != null) {
                        bond.setLotSizeType(
                                        request.getLotSizeType());
                }

                // -----------------------------------------------------
                // Save
                // -----------------------------------------------------

                Bond savedBond = bondRepository.save(bond);

                return mapToResponse(savedBond);
        }

        // =========================================================
        // ACTIVATE
        // =========================================================

        public BondResponse activateBond(UUID bondId)
                        throws BadRequestException {

                Bond bond = getEntity(bondId);

                // -----------------------------------------------------
                // Validate current state
                // -----------------------------------------------------

                if (bond.getStatus() == BondStatus.MATURED) {
                        throw new BadRequestException(
                                        "Matured bond cannot be activated");
                }

                if (bond.getStatus() == BondStatus.CANCELLED) {
                        throw new BadRequestException(
                                        "Cancelled bond cannot be activated");
                }

                if (bond.getMaturityType() == null) {
                        throw new BadRequestException(
                                        "Bond maturity type is required");
                }

                /*
                 * For fixed maturity bonds, maturity date is mandatory.
                 */
                if (bond.getMaturityType().name().equals("FIXED")
                                && bond.getMaturityDate() == null) {

                        throw new BadRequestException(
                                        "Bond maturity date is required");
                }

                bond.setStatus(BondStatus.ACTIVE);

                return mapToResponse(
                                bondRepository.save(bond));
        }

        // =========================================================
        // SUSPEND
        // =========================================================

        public BondResponse suspendBond(UUID bondId)
                        throws BadRequestException {

                Bond bond = getEntity(bondId);

                if (bond.getStatus() == BondStatus.MATURED) {
                        throw new BadRequestException(
                                        "Matured bond cannot be suspended");
                }

                if (bond.getStatus() == BondStatus.CANCELLED) {
                        throw new BadRequestException(
                                        "Cancelled bond cannot be suspended");
                }

                bond.setStatus(BondStatus.SUSPENDED);

                return mapToResponse(
                                bondRepository.save(bond));
        }

        // =========================================================
        // CANCEL
        // =========================================================

        public void cancelBond(UUID bondId)
                        throws BadRequestException {

                Bond bond = getEntity(bondId);

                if (bond.getStatus() == BondStatus.MATURED) {
                        throw new BadRequestException(
                                        "Matured bond cannot be cancelled");
                }

                if (bond.getStatus() == BondStatus.CANCELLED) {
                        throw new BadRequestException(
                                        "Bond is already cancelled");
                }

                /*
                 * Purchase/order validation should be added here
                 * once your investment/order model exists.
                 *
                 * Example:
                 *
                 * if (orderRepository.existsByBondIdAndActive(...)) {
                 * throw new BadRequestException(
                 * "Bond with existing purchases cannot be cancelled");
                 * }
                 */

                bond.setStatus(BondStatus.CANCELLED);

                bondRepository.save(bond);
        }

        // =========================================================
        // INTERNAL ENTITY LOOKUP
        // =========================================================

        private Bond getEntity(UUID id) {

                return bondRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Bond not found: " + id));
        }

        private Bond getbondByIs(String isIn) {
                return bondRepository.findByIsin(isIn)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Bond not found: " + isIn));
        }

        // =========================================================
        // INVALIDATE YIELD
        // =========================================================

        private void invalidateYieldCalculation(Bond bond) {

                bond.setSemiYtm(null);
                bond.setAnnualYtm(null);
                bond.setYtc(null);
                bond.setYtmCalculatedAt(null);
        }

        // =========================================================
        // ENTITY -> RESPONSE
        // =========================================================

        private BondResponse mapToResponse(Bond bond) {

                return BondResponse.builder()

                                .id(bond.getId())

                                .serialNumber(
                                                bond.getSerialNumber())

                                .name(
                                                bond.getName())

                                .isin(
                                                bond.getIsin())

                                // Classification
                                .category(
                                                bond.getCategory())

                                .securityType(
                                                bond.getSecurityType())

                                .rating(
                                                bond.getRating())

                                .ratingAgency(
                                                bond.getRatingAgency())

                                // Coupon
                                .couponRate(
                                                bond.getCouponRate())

                                .couponFrequency(
                                                bond.getCouponFrequency())

                                .ipDateDescription(
                                                bond.getIpDateDescription())

                                // Maturity
                                .maturityType(
                                                bond.getMaturityType())

                                .maturityDate(
                                                bond.getMaturityDate())

                                .maturityDescription(
                                                bond.getMaturityDescription())

                                .putCallDescription(
                                                bond.getPutCallDescription())

                                // Market
                                .price(
                                                bond.getPrice())

                                // Quantum
                                .quantumDescription(
                                                bond.getQuantumDescription())

                                .quantumInLacs(
                                                bond.getQuantumInLacs())

                                // Lot
                                .lotSizeDescription(
                                                bond.getLotSizeDescription())

                                .lotSize(
                                                bond.getLotSize())

                                .lotSizeType(
                                                bond.getLotSizeType())

                                // Status
                                .status(
                                                bond.getStatus())

                                // Audit
                                .createdAt(
                                                bond.getCreatedAt())

                                .updatedAt(
                                                bond.getUpdatedAt())

                                .build();
        }
}