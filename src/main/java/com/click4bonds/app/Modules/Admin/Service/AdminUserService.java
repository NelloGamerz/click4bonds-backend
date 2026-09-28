package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
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
import com.click4bonds.app.Modules.User.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

        private final UserRepository userRepository;
        private final ContactInquiryService contactInquiryService;

        @Transactional(readOnly = true)
        public Page<AdminUserSummaryResponse> getUsers(
                        UserRole role,
                        String search,
                        Pageable pageable) {

                Page<User> users;

                if (search == null || search.isBlank()) {
                        users = userRepository.findUsers(role, pageable);
                } else {
                        users = userRepository.searchUsers(
                                        role,
                                        search,
                                        pageable);
                }

                return users.map(this::toUserSummaryResponse);
        }

        @Transactional(readOnly = true)
        public AdminUserDetailsResponse getUserDetails(UUID userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                return toUserDetailsResponse(user);
        }

        public User updateUserStatus(
                        UUID userId,
                        UserStatus status) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                user.setStatus(status);

                return userRepository.save(user);
        }

        /**
         * Changes a user's role.
         *
         * <p>The change is the whole operation. It used to also enqueue an
         * outbox event so the role could be pushed to the external identity
         * provider that held the authoritative copy; that provider is gone and
         * this database is now the only place a role lives, so there is no
         * second system to notify. Authorization reads the role from here — via
         * the access token's {@code role} claim — so the change takes effect at
         * the user's next token refresh.</p>
         */
        public AdminUserDetailsResponse updateUserRole(
                UUID userId,
                UserRole role) {

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found"));

                if (user.getRole() != role) {
                        user.setRole(role);
                        user = userRepository.save(user);
                }

                return toUserDetailsResponse(user);
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
                                                                                .createdAt(verification.getCreatedAt())
                                                                                .updatedAt(verification.getUpdatedAt())
                                                                                .build())
                                .build();
        }

}
