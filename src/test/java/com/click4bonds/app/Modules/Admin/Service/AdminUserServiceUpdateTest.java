package com.click4bonds.app.Modules.Admin.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.UpdateUserRequest;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;
import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.AdminProfileRepository;
import com.click4bonds.app.Modules.User.Service.UserService;

/**
 * The single update endpoint, and what an omitted field means.
 *
 * <p>The rule that carries the most weight here is that absent means unchanged:
 * it is what lets a caller send only the field it is editing, and it is what
 * makes an empty grant set a different instruction from an absent one. Both are
 * asserted directly.</p>
 */
class AdminUserServiceUpdateTest {

    private final UserService userService = mock(UserService.class);

    private final ContactInquiryService contactInquiryService = mock(ContactInquiryService.class);

    private final AdminProfileRepository adminProfileRepository = mock(AdminProfileRepository.class);

    private final AdminUserService service =
            new AdminUserService(userService, contactInquiryService, adminProfileRepository);

    private static User staff(UserRole role) {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("staff@example.com")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();

        user.setAdminProfile(AdminProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .employeeCode("EMP-1")
                .department("Operations")
                .accessConfig(new LinkedHashSet<>(Set.of(AdminModule.ANALYTICS)))
                .build());

        return user;
    }

    private static User customer() {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        user.setCustomerProfile(CustomerProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .build());

        return user;
    }

    private User registered(User user) {

        when(userService.getUser(user.getId())).thenReturn(user);

        // Matchers rather than the account's current values: the point of these
        // tests is the value the caller asked for, not the one already on the
        // account, so the stub has to answer whatever is passed.
        when(userService.updateStatus(any(UUID.class), any(UserStatus.class))).thenReturn(user);
        when(userService.updateRole(any(UUID.class), any(UserRole.class))).thenReturn(user);

        return user;
    }

    // ------------------------------------------------------------------
    // Absent means unchanged
    // ------------------------------------------------------------------

    @Test
    void aRequestThatChangesNothingLeavesEverythingAlone() {

        User employee = registered(staff(UserRole.EMPLOYEE));

        // Every field null.
        AdminUserDetailsResponse response = service.updateUser(
                employee.getId(),
                new UpdateUserRequest(null, null, null, null, null, null));

        verify(userService, never()).updateStatus(employee.getId(), UserStatus.SUSPENDED);
        verify(userService, never()).updateRole(employee.getId(), UserRole.ADMIN);

        // The employment details and grants are as they were.
        assertEquals("EMP-1", employee.getAdminProfile().getEmployeeCode());
        assertEquals("Operations", employee.getAdminProfile().getDepartment());
        assertEquals(
                Set.of(AdminModule.ANALYTICS),
                employee.getAdminProfile().getAccessConfig());
    }

    @Test
    void anAbsentGrantSetLeavesTheGrantsAsTheyAre() {

        User employee = registered(staff(UserRole.EMPLOYEE));

        service.updateUser(
                employee.getId(),
                new UpdateUserRequest(null, null, null, null, "Senior Analyst", null));

        assertEquals("Senior Analyst", employee.getAdminProfile().getDesignation());
        assertEquals(
                Set.of(AdminModule.ANALYTICS),
                employee.getAdminProfile().getAccessConfig());
    }

    @Test
    void anEmptyGrantSetWithdrawsEveryModule() {

        User employee = registered(staff(UserRole.EMPLOYEE));

        service.updateUser(
                employee.getId(),
                new UpdateUserRequest(null, null, null, null, null, Set.of()));

        // Distinct from absent, and the distinction is the point: the account
        // keeps its employee details and loses every grant.
        assertEquals(0, employee.getAdminProfile().getAccessConfig().size());
        assertEquals("EMP-1", employee.getAdminProfile().getEmployeeCode());
    }

    @Test
    void grantsAreReplacedWholesale() {

        User employee = registered(staff(UserRole.EMPLOYEE));

        service.updateUser(
                employee.getId(),
                new UpdateUserRequest(
                        null, null, null, null, null,
                        Set.of(AdminModule.BOND_MANAGEMENT, AdminModule.ORDER_MANAGEMENT)));

        assertEquals(
                Set.of(AdminModule.BOND_MANAGEMENT, AdminModule.ORDER_MANAGEMENT),
                employee.getAdminProfile().getAccessConfig());
    }

    // ------------------------------------------------------------------
    // Status and role
    // ------------------------------------------------------------------

    @Test
    void aStatusChangeIsApplied() {

        User employee = registered(staff(UserRole.EMPLOYEE));

        service.updateUser(
                employee.getId(),
                new UpdateUserRequest(UserStatus.SUSPENDED, null, null, null, null, null));

        verify(userService).updateStatus(employee.getId(), UserStatus.SUSPENDED);
    }

    @Test
    void aRoleChangeBetweenTheTwoStaffRolesIsApplied() {

        User employee = registered(staff(UserRole.EMPLOYEE));

        service.updateUser(
                employee.getId(),
                new UpdateUserRequest(null, UserRole.ADMIN, null, null, null, null));

        verify(userService).updateRole(employee.getId(), UserRole.ADMIN);
    }

    @Test
    void aRoleChangeAcrossTheCustomerStaffLineIsRefused() {

        User customer = registered(customer());

        // UserService refuses this rather than discarding the customer profile.
        when(userService.updateRole(customer.getId(), UserRole.EMPLOYEE))
                .thenThrow(new com.click4bonds.app.Modules.Common.Exceptions.ConflictException(
                        "cannot cross"));

        assertThrows(
                com.click4bonds.app.Modules.Common.Exceptions.ConflictException.class,
                () -> service.updateUser(
                        customer.getId(),
                        new UpdateUserRequest(null, UserRole.EMPLOYEE, null, null, null, null)));
    }

    // ------------------------------------------------------------------
    // Staff fields against a customer
    // ------------------------------------------------------------------

    @Test
    void staffFieldsAgainstACustomerAreRefusedRatherThanIgnored() {

        User customer = registered(customer());

        // A caller that sends a department for a customer has mistaken what it is
        // editing. Discarding it would let the mistake read as a success.
        assertThrows(
                BadRequestException.class,
                () -> service.updateUser(
                        customer.getId(),
                        new UpdateUserRequest(null, null, null, "Operations", null, null)));

        assertThrows(
                BadRequestException.class,
                () -> service.updateUser(
                        customer.getId(),
                        new UpdateUserRequest(
                                null, null, null, null, null, Set.of(AdminModule.ANALYTICS))));

        // The staff profile is never touched, because the request was refused
        // before any of it was written.
        verifyNoInteractions(adminProfileRepository);
    }

    @Test
    void aCustomerStatusChangeIsStillAllowed() {

        User customer = registered(customer());

        // Only the staff fields are staff-only; suspending a customer is exactly
        // what this endpoint is for.
        service.updateUser(
                customer.getId(),
                new UpdateUserRequest(UserStatus.SUSPENDED, null, null, null, null, null));

        verify(userService).updateStatus(customer.getId(), UserStatus.SUSPENDED);
    }
}
