package com.click4bonds.app.Modules.User.Mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * {@code GET /auth/me} carries the profile the account's role implies, and only
 * that one.
 */
class UserMapperTest {

    @Test
    void customerGetsTheirCustomerProfileAndNoAdminProfile() {

        UUID partyId = UUID.randomUUID();
        CustomerProfile profile = CustomerProfile.builder()
                .id(UUID.randomUUID())
                .party(Party.builder().id(partyId).build())
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.CUSTOMER)
                .customerProfile(profile)
                .build();

        UserResponse response = UserMapper.toUserResponse(user);

        assertNotNull(response.getCustomerProfile());
        assertEquals(partyId, response.getCustomerProfile().partyId());
        assertNull(response.getAdminProfile());
    }

    @Test
    void staffGetsTheirAdminProfileAndNoCustomerProfile() {

        AdminProfile profile = AdminProfile.builder()
                .id(UUID.randomUUID())
                .emailVerified(true)
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.EMPLOYEE)
                .adminProfile(profile)
                .build();

        UserResponse response = UserMapper.toUserResponse(user);

        assertNotNull(response.getAdminProfile());
        assertEquals(true, response.getAdminProfile().emailVerified());
        assertNull(response.getCustomerProfile());
    }

    @Test
    void theRoleDecidesEvenWhenTheOtherAssociationIsPopulated() {

        User staff = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.ADMIN)
                .customerProfile(CustomerProfile.builder().id(UUID.randomUUID()).build())
                .build();

        UserResponse staffResponse = UserMapper.toUserResponse(staff);

        assertNull(staffResponse.getCustomerProfile());
        assertNull(staffResponse.getAdminProfile());

        User customer = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.CUSTOMER)
                .customerProfile(CustomerProfile.builder().id(UUID.randomUUID()).build())
                .adminProfile(AdminProfile.builder().id(UUID.randomUUID()).build())
                .build();

        UserResponse customerResponse = UserMapper.toUserResponse(customer);

        assertNotNull(customerResponse.getCustomerProfile());
        assertNull(customerResponse.getAdminProfile());
    }
}
