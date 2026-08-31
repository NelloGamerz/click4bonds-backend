package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Dto.UserRoleChangedEvent;
import com.click4bonds.app.Modules.Common.Enums.OutboxStatus;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Common.Model.OutboxEvent;
import com.click4bonds.app.Modules.Common.Repository.OutboxEventRepository;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

        private final UserRepository userRepository;
        private final OutboxEventRepository outboxEventRepository;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final ContactInquiryService contactInquiryService;

        @Transactional(readOnly = true)
        public Page<User> getCustomers(
                        String search,
                        Pageable pageable) {

                if (search == null || search.isBlank()) {
                        return userRepository.findByRole(
                                        UserRole.CUSTOMER,
                                        pageable);
                }

                return userRepository.searchUsers(
                                UserRole.CUSTOMER,
                                search,
                                pageable);
        }

        @Transactional(readOnly = true)
        public Page<User> getEmployees(
                        Pageable pageable) {

                return userRepository.findByRole(
                                UserRole.EMPLOYEE,
                                pageable);
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

        public User updateUserRole(
                        UUID userId,
                        UserRole role) throws JsonProcessingException {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                if (user.getRole() == role) {
                        return user;
                }

                user.setRole(role);

                User savedUser = userRepository.save(user);

                UserRoleChangedEvent event = new UserRoleChangedEvent(
                                savedUser.getId(),
                                savedUser.getClerkUserId(),
                                savedUser.getRole());

                OutboxEvent outboxEvent = OutboxEvent.builder()
                                .eventType("USER_ROLE_CHANGED")
                                .aggregateType("USER")
                                .aggregateId(savedUser.getId())
                                .payload(
                                                objectMapper.writeValueAsString(event))
                                .status(OutboxStatus.PENDING)
                                .retryCount(0)
                                .build();

                outboxEventRepository.save(outboxEvent);

                return savedUser;
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

}
