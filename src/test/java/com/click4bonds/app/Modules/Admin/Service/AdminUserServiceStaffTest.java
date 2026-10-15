package com.click4bonds.app.Modules.Admin.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateStaffUserRequest;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;
import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.AdminProfileRepository;
import com.click4bonds.app.Modules.User.Service.UserService;

/**
 * Opening a staff account, and what it is refused.
 *
 * <p>The interesting part is not that an administrator can create an employee —
 * it is what the account comes out as: no customer profile, an admin profile
 * with an unconfirmed address, and active from creation so the person can sign
 * in and confirm it.</p>
 */
class AdminUserServiceStaffTest {

    private final UserService userService = mock(UserService.class);

    private final ContactInquiryService contactInquiryService = mock(ContactInquiryService.class);

    private final AdminProfileRepository adminProfileRepository = mock(AdminProfileRepository.class);

    private final AdminUserService service =
            new AdminUserService(userService, contactInquiryService, adminProfileRepository);

    private AdminUserServiceStaffTest() {

        // Stands in for what UserService.createUser does with the profile and
        // the database-assigned identifier. The invariant itself is covered by
        // UserServiceProfileInvariantTest; here the concern is what this service
        // asks it for.
        when(userService.createUser(any(User.class))).thenAnswer(invocation -> {

            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());

            if (user.getRole() != UserRole.CUSTOMER && user.getAdminProfile() == null) {
                user.setAdminProfile(com.click4bonds.app.Modules.User.Model.AdminProfile.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .build());
            }

            return user;
        });
    }

    private static CreateStaffUserRequest request(UserRole role) {
        return new CreateStaffUserRequest(
                "New.Staff@Example.com", "Nisha", "Rao", role, null, null, null, null);
    }

    @Test
    void anEmployeeAccountIsCreatedWithAnAdminProfileAndNoCustomerProfile() {

        AdminUserDetailsResponse response = service.createStaffUser(request(UserRole.EMPLOYEE));

        assertNotNull(response.getId());
        assertEquals(UserRole.EMPLOYEE, response.getRole());
        assertEquals(UserStatus.ACTIVE, response.getStatus());

        // No customer record at all, and no onboarding step to report: an
        // employee does not onboard.
        assertNull(response.getOnboardingStep());
        assertNull(response.getVerification());
    }

    @Test
    void theAddressIsStoredInCanonicalFormAndStartsUnconfirmed() {

        service.createStaffUser(request(UserRole.EMPLOYEE));

        org.mockito.ArgumentCaptor<User> captured =
                org.mockito.ArgumentCaptor.forClass(User.class);

        verify(userService).createUser(captured.capture());

        // Normalised the same way the OTP module normalises a submission, so
        // signing in with the address as typed finds the account.
        assertEquals("new.staff@example.com", captured.getValue().getEmail());

        // Nobody has proven they can receive mail there yet — the administrator
        // typed it.
        assertFalse(captured.getValue().getAdminProfile().getEmailVerified());
        assertNull(captured.getValue().getCustomerProfile());
    }

    @Test
    void aCustomerRoleIsRefused() {

        // The role type admits CUSTOMER and this endpoint does not: a customer
        // made here would have none of what sign-up gives one — the profile,
        // the onboarding state and the verification record.
        assertThrows(BadRequestException.class, () -> service.createStaffUser(request(UserRole.CUSTOMER)));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    void anAddressAnotherAccountAlreadyOwnsIsRefused() {

        when(userService.isEmailClaimed("new.staff@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createStaffUser(request(UserRole.EMPLOYEE)));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    void anAdminCanBeCreatedDirectly() {

        AdminUserDetailsResponse response = service.createStaffUser(request(UserRole.ADMIN));

        assertEquals(UserRole.ADMIN, response.getRole());
        assertTrue(response.getRole() == UserRole.ADMIN);
    }

    @Test
    void theEmploymentDetailsAndGrantsComeWithTheCreateRequest() {

        service.createStaffUser(new CreateStaffUserRequest(
                "new.staff@example.com",
                "Nisha",
                "Rao",
                UserRole.EMPLOYEE,
                "EMP-1042",
                "Operations",
                "Analyst",
                java.util.Set.of(AdminModule.ANALYTICS, AdminModule.BOND_MANAGEMENT)));

        org.mockito.ArgumentCaptor<User> captured =
                org.mockito.ArgumentCaptor.forClass(User.class);

        verify(userService).createUser(captured.capture());

        var profile = captured.getValue().getAdminProfile();

        // Everything the request carried is on the profile, so an account is
        // opened complete rather than created bare and corrected twice after.
        assertEquals("EMP-1042", profile.getEmployeeCode());
        assertEquals("Operations", profile.getDepartment());
        assertEquals("Analyst", profile.getDesignation());
        assertEquals(
                java.util.Set.of(AdminModule.ANALYTICS, AdminModule.BOND_MANAGEMENT),
                profile.getAccessConfig());
    }

    @Test
    void aBlankEmploymentFieldIsStoredAsAbsentRatherThanAsWhitespace() {

        service.createStaffUser(new CreateStaffUserRequest(
                "new.staff@example.com",
                "Nisha",
                "Rao",
                UserRole.EMPLOYEE,
                "   ",
                "",
                null,
                null));

        org.mockito.ArgumentCaptor<User> captured =
                org.mockito.ArgumentCaptor.forClass(User.class);

        verify(userService).createUser(captured.capture());

        var profile = captured.getValue().getAdminProfile();

        assertNull(profile.getEmployeeCode());
        assertNull(profile.getDepartment());
        assertNull(profile.getDesignation());
    }
}
