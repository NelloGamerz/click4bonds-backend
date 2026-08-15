package com.click4bonds.app.Modules.Bond.Service;

import java.util.Objects;
import java.util.UUID;

import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
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

@Service
@RequiredArgsConstructor
@Transactional
public class BondService {

        private final BondRepository bondRepository;
        private final UserRepository userRepository;

        public BondResponse createBond(
                        CreateBondRequest request,
                        String adminId) throws BadRequestException {

                if (bondRepository.existsByIsin(request.getIsin())) {
                        throw new ConflictException(
                                        "Bond with ISIN already exists");
                }

                if (!request.getMaturityDate().isAfter(request.getIssueDate())) {
                        throw new BadRequestException(
                                        "Maturity date must be after issue date");
                }

                User admin = userRepository.findByClerkUserId(adminId)
                                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

                Bond bond = Bond.builder()
                                .isin(request.getIsin())
                                .name(request.getName())
                                .issuer(request.getIssuer())
                                .description(request.getDescription())
                                .faceValue(request.getFaceValue())
                                .couponRate(request.getCouponRate())
                                .couponFrequency(request.getCouponFrequency())
                                .issueDate(request.getIssueDate())
                                .maturityDate(request.getMaturityDate())
                                .sellingPrice(request.getSellingPrice())
                                .minimumInvestment(request.getMinimumInvestment())
                                .totalUnits(request.getTotalUnits())
                                .availableUnits(request.getTotalUnits())
                                .status(BondStatus.DRAFT)
                                .createdBy(admin)
                                .build();

                return mapToResponse(
                                bondRepository.save(bond));
        }

        @Transactional(readOnly = true)
        public BondResponse getBond(UUID id) {

                Bond bond = bondRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Bond not found: " + id));

                return mapToResponse(bond);
        }

        @Transactional(readOnly = true)
        public Page<BondResponse> getBonds(Pageable pageable) {

                return bondRepository.findAll(pageable)
                                .map(this::mapToResponse);
        }

        @Transactional(readOnly = true)
        public Page<BondResponse> getActiveBonds(Pageable pageable) {

                return bondRepository
                                .findByStatus(BondStatus.ACTIVE, pageable)
                                .map(this::mapToResponse);
        }

        public BondResponse updateBond(
                        UUID bondId,
                        UpdateBondRequest request) throws BadRequestException {

                Bond bond = bondRepository.findById(bondId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Bond not found: " + bondId));

                if (bond.getStatus() == BondStatus.MATURED) {
                        throw new BadRequestException(
                                        "Matured bond cannot be updated");
                }

                if (request.getName() != null) {
                        bond.setName(request.getName());
                }

                if (request.getIssuer() != null) {
                        bond.setIssuer(request.getIssuer());
                }

                if (request.getDescription() != null) {
                        bond.setDescription(request.getDescription());
                }

                if (request.getFaceValue() != null) {
                        bond.setFaceValue(request.getFaceValue());
                }

                if (request.getCouponRate() != null) {
                        bond.setCouponRate(request.getCouponRate());
                }

                if (request.getCouponFrequency() != null) {
                        bond.setCouponFrequency(request.getCouponFrequency());
                }

                if (request.getIssueDate() != null) {
                        bond.setIssueDate(request.getIssueDate());
                }

                if (request.getMaturityDate() != null) {
                        bond.setMaturityDate(request.getMaturityDate());
                }

                if (request.getSellingPrice() != null) {
                        bond.setSellingPrice(request.getSellingPrice());
                }

                if (request.getMinimumInvestment() != null) {
                        bond.setMinimumInvestment(request.getMinimumInvestment());
                }

                return mapToResponse(bondRepository.save(bond));
        }

        public BondResponse activateBond(UUID bondId) throws BadRequestException {

                Bond bond = getEntity(bondId);

                if (bond.getAvailableUnits() <= 0) {
                        throw new BadRequestException(
                                        "Cannot activate a sold-out bond");
                }

                bond.setStatus(BondStatus.ACTIVE);

                return mapToResponse(
                                bondRepository.save(bond));
        }

        public BondResponse suspendBond(UUID bondId) {

                Bond bond = getEntity(bondId);

                bond.setStatus(BondStatus.SUSPENDED);

                return mapToResponse(
                                bondRepository.save(bond));
        }

        public void cancelBond(UUID bondId) throws BadRequestException {

                Bond bond = getEntity(bondId);

                // if (bond.getAvailableUnits() != bond.getTotalUnits()) {
                // throw new BadRequestException(
                // "Bond with existing purchases cannot be deleted");
                // }
                if (!Objects.equals(bond.getAvailableUnits(), bond.getTotalUnits())) {
                        throw new BadRequestException(
                                        "Bond with existing purchases cannot be cancelled");
                }

                bond.setStatus(BondStatus.CANCELLED);

                bondRepository.save(bond);
        }

        private Bond getEntity(UUID id) {

                return bondRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Bond not found: " + id));
        }

        private BondResponse mapToResponse(Bond bond) {

                return BondResponse.builder()
                                .id(bond.getId())
                                .isin(bond.getIsin())
                                .name(bond.getName())
                                .issuer(bond.getIssuer())
                                .description(bond.getDescription())
                                .faceValue(bond.getFaceValue())
                                .couponRate(bond.getCouponRate())
                                .couponFrequency(bond.getCouponFrequency())
                                .issueDate(bond.getIssueDate())
                                .maturityDate(bond.getMaturityDate())
                                .sellingPrice(bond.getSellingPrice())
                                .minimumInvestment(bond.getMinimumInvestment())
                                .totalUnits(bond.getTotalUnits())
                                .availableUnits(bond.getAvailableUnits())
                                .status(bond.getStatus())
                                .createdAt(bond.getCreatedAt())
                                .updatedAt(bond.getUpdatedAt())
                                .build();
        }

}