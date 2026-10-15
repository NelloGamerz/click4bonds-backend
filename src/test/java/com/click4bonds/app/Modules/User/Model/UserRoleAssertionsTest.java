package com.click4bonds.app.Modules.User.Model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.User.Enums.UserRole;

/**
 * The rule that a reference to an account is the right kind of account.
 *
 * <p>Tested directly rather than through the entities that call it, so the cases
 * that matter — a customer where staff is required, and the reverse — are stated
 * without a JPA provider in the way. The entities only delegate.</p>
 */
class UserRoleAssertionsTest {

    private static User account(UserRole role) {

        return User.builder()
                .id(UUID.randomUUID())
                .role(role)
                .build();
    }

    @Test
    void aCustomerSatisfiesTheCustomerRule() {

        assertDoesNotThrow(() ->
                UserRoleAssertions.requireCustomer(account(UserRole.CUSTOMER), "BondOrder"));
    }

    @Test
    void aMemberOfStaffDoesNotSatisfyTheCustomerRule() {

        // The case the guard exists for: an order or a holding built for the
        // account that happened to be authenticated rather than for a buyer.
        assertThrows(IllegalArgumentException.class, () ->
                UserRoleAssertions.requireCustomer(account(UserRole.ADMIN), "BondOrder"));

        assertThrows(IllegalArgumentException.class, () ->
                UserRoleAssertions.requireCustomer(account(UserRole.EMPLOYEE), "BondHolding"));
    }

    @Test
    void anAdminAndAnEmployeeBothSatisfyTheStaffRule() {

        assertDoesNotThrow(() ->
                UserRoleAssertions.requireStaff(account(UserRole.ADMIN), "AuditLog"));

        assertDoesNotThrow(() ->
                UserRoleAssertions.requireStaff(account(UserRole.EMPLOYEE), "AuditLog"));
    }

    @Test
    void aCustomerDoesNotSatisfyTheStaffRule() {

        assertThrows(IllegalArgumentException.class, () ->
                UserRoleAssertions.requireStaff(account(UserRole.CUSTOMER), "AuditLog"));
    }

    @Test
    void anAbsentAccountSatisfiesNeitherRule() {

        assertThrows(IllegalArgumentException.class, () ->
                UserRoleAssertions.requireCustomer(null, "BondOrder"));

        assertThrows(IllegalArgumentException.class, () ->
                UserRoleAssertions.requireStaff(null, "AuditLog"));
    }

    @Test
    void theMessageNamesTheEntityAndTheAccountRefused() {

        User admin = account(UserRole.ADMIN);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                UserRoleAssertions.requireCustomer(admin, "BondOrder"));

        // Both, because a refusal is only actionable if it says which entity
        // rejected it and which account it rejected.
        assertTrue(thrown.getMessage().contains("BondOrder"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(admin.getId().toString()), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("ADMIN"), thrown.getMessage());
    }
}
