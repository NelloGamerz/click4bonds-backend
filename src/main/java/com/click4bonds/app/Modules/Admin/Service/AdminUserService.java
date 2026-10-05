package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.User.Dto.UserVerificationResponse;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;
import com.click4bonds.app.Modules.User.Service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

        private final UserService userService;
        private final ContactInquiryService contactInquiryService;

        @Transactional(readOnly = true)
        public Page<AdminUserSummaryResponse> getUsers(
                        UserRole role,
                        String search,
                        Pageable pageable) {

                return userService.getUsers(role, search, pageable)
                                .map(this::toUserSummaryResponse);
        }

        @Transactional(readOnly = true)
        public AdminUserDetailsResponse getUserDetails(UUID userId) {

                return toUserDetailsResponse(userService.getUser(userId));
        }

        public AdminUserDetailsResponse updateUserStatus(
                        UUID userId,
                        UserStatus status) {

                return toUserDetailsResponse(userService.updateStatus(userId, status));
        }

        public AdminUserDetailsResponse updateUserRole(
                        UUID userId,
                        UserRole role) {

                return toUserDetailsResponse(userService.updateRole(userId, role));
        }

        @Transactional(readOnly = true)
        public Page<ContactInquiryAdminResponse> getContactInquiries(
                        ContactInquiryStatus status,
                        Pageable pageable) {

                if (status == null) {
                        return contactInquiryService.getAllInquiries(pageable);
                }

                return contactInquiryService.getInquiriesByStatus(
                                status,
                                pageable);
        }

        public ContactInquiryAdminResponse updateContactInquiryStatus(
                        UUID inquiryId,
                        ContactInquiryStatus status) {

                return contactInquiryService.updateStatus(
                                inquiryId,
                                status);
        }

        private AdminUserSummaryResponse toUserSummaryResponse(User user) {

                return AdminUserSummaryResponse.builder()
                                .id(user.getId())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .email(user.getEmail())
                                .role(user.getRole())
                                .status(user.getStatus())
                                .updatedAt(user.getUpdatedAt())
                                .build();
        }

        private AdminUserDetailsResponse toUserDetailsResponse(User user) {

                UserVerification verification = user.getVerification();

                return AdminUserDetailsResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .mobileNumber(user.getMobileNumber())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .profileImage(user.getProfileImage())
                                .onboardingStep(user.getOnboardingStep())
                                .role(user.getRole())
                                .status(user.getStatus())
                                .createdAt(user.getCreatedAt())
                                .updatedAt(user.getUpdatedAt())
                                .verification(
                                                verification == null
                                                                ? null
                                                                : UserVerificationResponse.builder()
                                                                                .id(verification.getId())
                                                                                .emailStatus(verification
                                                                                                .getEmailStatus())
                                                                                .phoneStatus(verification
                                                                                                .getPhoneStatus())
                                                                                .panStatus(verification.getPanStatus())
                                                                                .bankAccountStatus(verification
                                                                                                .getBankAccountStatus())
                                                                                .dematStatus(verification
                                                                                                .getDematStatus())
                                                                                .createdAt(verification.getCreatedAt())
                                                                                .updatedAt(verification.getUpdatedAt())
                                                                                .build())
                                .build();
        }

}
