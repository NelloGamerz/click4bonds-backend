package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.Admin.Mapper.AdminUserMapper;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
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
                .map(AdminUserMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailsResponse getUserDetails(UUID userId) {

        return AdminUserMapper.toDetails(userService.getUser(userId));
    }

    public AdminUserDetailsResponse updateUserStatus(
            UUID userId,
            UserStatus status) {

        return AdminUserMapper.toDetails(userService.updateStatus(userId, status));
    }

    public AdminUserDetailsResponse updateUserRole(
            UUID userId,
            UserRole role) {

        return AdminUserMapper.toDetails(userService.updateRole(userId, role));
    }

    @Transactional(readOnly = true)
    public Page<ContactInquiryAdminResponse> getContactInquiries(
            ContactInquiryStatus status,
            Pageable pageable) {

        return contactInquiryService.getInquiries(status, pageable);
    }

    public ContactInquiryAdminResponse updateContactInquiryStatus(
            UUID inquiryId,
            ContactInquiryStatus status) {

        return contactInquiryService.updateStatus(
                inquiryId,
                status);
    }

}
