package com.click4bonds.app.Modules.Admin.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.AdminProfileRepository;
import com.click4bonds.app.Modules.User.Repository.UserRepository;

/**
 * What a member of staff may use, decided on its own without a Spring context.
 *
 * <p>The cases that matter are the ones where the answer is no: a customer, an
 * employee with no grant, an employee with the wrong grant, an account that no
 * longer exists, a token whose subject is not an identifier, a module name that
 * is not one of the constants, and a staff account with no profile. Each of
 * those has to refuse rather than fall through, because a permission check that
 * fails open is not a permission check.</p>
 */
class AdminPermissionServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private final AdminProfileRepository adminProfileRepository = mock(AdminProfileRepository.class);

    private final AdminPermissionService service =
            new AdminPermissionService(userRepository, adminProfileRepository);

    private static Authentication authenticationFor(UUID userId) {

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId == null ? null : userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        return new UsernamePasswordAuthenticationToken(jwt, null, AuthorityUtils.NO_AUTHORITIES);
    }

    private User account(UUID id, UserRole role) {

        User user = User.builder()
                .id(id)
                .role(role)
                .build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        return user;
    }

    private void grants(User user, Set<AdminModule> modules) {

        when(adminProfileRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(AdminProfile.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .accessConfig(new LinkedHashSet<>(modules))
                        .build()));
    }

    // ------------------------------------------------------------------
    // Allowed
    // ------------------------------------------------------------------

    @Test
    void anAdminIsAllowedEveryModuleWithoutConsultingItsGrants() {

        UUID id = UUID.randomUUID();
        account(id, UserRole.ADMIN);

        Authentication authentication = authenticationFor(id);

        assertTrue(service.hasModule(authentication, "ANALYTICS"));
        assertTrue(service.hasModule(authentication, "USER_MANAGEMENT"));
        assertTrue(service.hasModule(authentication, "BOND_MANAGEMENT"));

        // Not merely allowed — not asked. A grant that could constrain an
        // administrator could lock them out of the surface that sets grants.
        verifyNoInteractions(adminProfileRepository);
    }

    @Test
    void anEmployeeIsAllowedAModuleItHasBeenGranted() {

        UUID id = UUID.randomUUID();
        User employee = account(id, UserRole.EMPLOYEE);
        grants(employee, Set.of(AdminModule.ANALYTICS));

        assertTrue(service.hasModule(authenticationFor(id), "ANALYTICS"));
    }

    // ------------------------------------------------------------------
    // Refused
    // ------------------------------------------------------------------

    @Test
    void anEmployeeIsRefusedAModuleItHasNotBeenGranted() {

        UUID id = UUID.randomUUID();
        User employee = account(id, UserRole.EMPLOYEE);
        grants(employee, Set.of(AdminModule.ANALYTICS));

        assertFalse(service.hasModule(authenticationFor(id), "USER_MANAGEMENT"));
    }

    @Test
    void anEmployeeWithNoGrantsAtAllIsRefused() {

        UUID id = UUID.randomUUID();
        User employee = account(id, UserRole.EMPLOYEE);

        // An empty set, which is what every existing staff account has.
        grants(employee, Set.of());

        assertFalse(service.hasModule(authenticationFor(id), "ANALYTICS"));
    }

    @Test
    void aStaffAccountWithNoProfileIsRefused() {

        UUID id = UUID.randomUUID();
        account(id, UserRole.EMPLOYEE);

        // No stubbing of the profile repository: it answers with an empty
        // Optional, as it would for a staff account the migration had not
        // reached.
        assertFalse(service.hasModule(authenticationFor(id), "ANALYTICS"));
    }

    @Test
    void aCustomerIsRefusedEvenIfItHasGrantsStored() {

        UUID id = UUID.randomUUID();
        User customer = account(id, UserRole.CUSTOMER);
        grants(customer, Set.of(AdminModule.ANALYTICS, AdminModule.USER_MANAGEMENT));

        // The role decides before the grants are read. A customer is not a
        // member of staff, so no stored set can make one.
        assertFalse(service.hasModule(authenticationFor(id), "ANALYTICS"));
    }

    @Test
    void anUnknownModuleNameIsRefusedRatherThanFaulted() {

        UUID id = UUID.randomUUID();
        User employee = account(id, UserRole.EMPLOYEE);
        grants(employee, Set.of(AdminModule.ANALYTICS));

        // The names are string literals in @PreAuthorize expressions, which
        // nothing type-checks. A typo must deny, not throw.
        assertFalse(service.hasModule(authenticationFor(id), "ANALYTIC"));
        assertFalse(service.hasModule(authenticationFor(id), "analytics"));
        assertFalse(service.hasModule(authenticationFor(id), ""));
        assertFalse(service.hasModule(authenticationFor(id), null));
    }

    @Test
    void aTokenForAnAccountThatDoesNotExistIsRefused() {

        UUID id = UUID.randomUUID();

        // No stubbing: the repository finds nothing.
        assertFalse(service.hasModule(authenticationFor(id), "ANALYTICS"));
    }

    @Test
    void aSubjectThatIsNotAnIdentifierIsRefused() {

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(jwt, null, AuthorityUtils.NO_AUTHORITIES);

        assertFalse(service.hasModule(authentication, "ANALYTICS"));
    }

    @Test
    void anUnauthenticatedCallerIsRefused() {

        assertFalse(service.hasModule(null, "ANALYTICS"));

        Authentication anonymous =
                new UsernamePasswordAuthenticationToken("someone", null, AuthorityUtils.NO_AUTHORITIES);

        // Two-argument constructor: not authenticated.
        assertFalse(service.hasModule(anonymous, "ANALYTICS"));
    }

    @Test
    void aPrincipalThatIsNotAJwtIsRefused() {

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "a-principal-of-another-kind", null, AuthorityUtils.NO_AUTHORITIES);

        assertFalse(service.hasModule(authentication, "ANALYTICS"));
    }

    // ------------------------------------------------------------------
    // Revocation
    // ------------------------------------------------------------------

    @Test
    void withdrawingAGrantTakesEffectOnTheNextRequestWithoutANewToken() {

        UUID id = UUID.randomUUID();
        User employee = account(id, UserRole.EMPLOYEE);

        grants(employee, Set.of(AdminModule.ANALYTICS));

        // One token, two requests. Nothing about the credential changes between
        // them — which is the whole reason the grants are read from the
        // database rather than carried in the token.
        Authentication authentication = authenticationFor(id);

        assertTrue(service.hasModule(authentication, "ANALYTICS"));

        grants(employee, Set.of());

        assertFalse(service.hasModule(authentication, "ANALYTICS"));
    }

    @Test
    void demotingAnAdminTakesEffectOnTheNextRequestWithoutANewToken() {

        UUID id = UUID.randomUUID();
        User account = account(id, UserRole.ADMIN);

        Authentication authentication = authenticationFor(id);

        assertTrue(service.hasModule(authentication, "ANALYTICS"));

        // The role is re-read from the account too, not taken from the token's
        // claim, so this is immediate as well.
        account.setRole(UserRole.EMPLOYEE);
        grants(account, Set.of());

        assertFalse(service.hasModule(authentication, "ANALYTICS"));
    }
}
