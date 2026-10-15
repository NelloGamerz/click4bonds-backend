package com.click4bonds.app.Modules.User.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.UserRepository;

/**
 * The rule that an account has the profile its role implies, and only that one.
 *
 * <p>Stated without a Spring context or a database, because the rule is a
 * decision this service makes rather than something the schema enforces — the
 * schema can only say that a profile belongs to one account, not which kind of
 * profile that account is entitled to.</p>
 */
class UserServiceProfileInvariantTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private final UserVerificationService userVerificationService = mock(UserVerificationService.class);

    private final AnalyticsService analyticsService = mock(AnalyticsService.class);

    private final UserService service =
            new UserService(userRepository, userVerificationService, analyticsService);

    private UserServiceProfileInvariantTest() {

        // The real save hands back a managed entity; the callers here read the
        // identifier off what comes back.
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static User account(UserRole role) {

        return User.builder()
                .id(UUID.randomUUID())
                .role(role)
                .build();
    }

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    @Test
    void aCustomerIsCreatedWithACustomerProfileAndAVerificationRecord() {

        User saved = service.createUser(account(UserRole.CUSTOMER));

        CustomerProfile profile = saved.getCustomerProfile();

        assertNotNull(profile);
        assertNull(saved.getAdminProfile());

        // Both directions, because the profile owns the foreign key and a
        // profile with a null user_id would not persist.
        assertSame(saved, profile.getUser());

        verify(userVerificationService).createVerification(profile);
    }

    @Test
    void anAdminIsCreatedWithAnAdminProfileAndNoVerificationRecord() {

        User saved = service.createUser(account(UserRole.ADMIN));

        AdminProfile profile = saved.getAdminProfile();

        assertNotNull(profile);
        assertNull(saved.getCustomerProfile());
        assertSame(saved, profile.getUser());

        // A staff account is not asked to prove an email, a phone, a PAN, a bank
        // account or a demat account, so it has no channels to track.
        assertNotNull(profile.getAccessConfig());
        assertEquals(0, profile.getAccessConfig().size());
        verifyNoInteractions(userVerificationService);
    }

    @Test
    void anEmployeeIsCreatedWithAnAdminProfile() {

        User saved = service.createUser(account(UserRole.EMPLOYEE));

        assertNotNull(saved.getAdminProfile());
        assertNull(saved.getCustomerProfile());

        verifyNoInteractions(userVerificationService);
    }

    @Test
    void aProfileTheCallerSuppliedIsAdoptedRatherThanReplaced() {

        // Sign-up builds the profile from the form before handing the account
        // over, so the answers have to survive creation.
        CustomerProfile supplied = CustomerProfile.builder()
                .id(UUID.randomUUID())
                .onboardingStep(OnboardingStep.PHONE_VERIFICATION)
                .build();

        User account = account(UserRole.CUSTOMER);
        account.setCustomerProfile(supplied);

        User saved = service.createUser(account);

        assertSame(supplied, saved.getCustomerProfile());
        assertSame(saved, supplied.getUser());
    }

    @Test
    void aCustomerCarryingAnAdminProfileIsRefused() {

        User account = account(UserRole.CUSTOMER);
        account.setAdminProfile(AdminProfile.builder().build());

        assertThrows(IllegalStateException.class, () -> service.createUser(account));
    }

    @Test
    void aMemberOfStaffCarryingACustomerProfileIsRefused() {

        User account = account(UserRole.EMPLOYEE);
        account.setCustomerProfile(CustomerProfile.builder().build());

        assertThrows(IllegalStateException.class, () -> service.createUser(account));
    }

    // ------------------------------------------------------------------
    // Role changes
    // ------------------------------------------------------------------

    @Test
    void aChangeAcrossTheCustomerStaffLineIsRefused() {

        UUID id = UUID.randomUUID();

        User customer = account(UserRole.CUSTOMER);
        customer.setId(id);
        customer.setCustomerProfile(CustomerProfile.builder().user(customer).build());

        when(userRepository.findById(id)).thenReturn(Optional.of(customer));

        // Refused rather than performed, because performing it would delete the
        // KYC state, the consents and the verification record — data loss the
        // caller cannot see from a single role parameter.
        assertThrows(ConflictException.class, () -> service.updateRole(id, UserRole.EMPLOYEE));

        // And nothing was changed on the way to refusing.
        assertEquals(UserRole.CUSTOMER, customer.getRole());
        assertNotNull(customer.getCustomerProfile());
    }

    @Test
    void aChangeFromStaffToCustomerIsRefusedToo() {

        UUID id = UUID.randomUUID();

        User admin = account(UserRole.ADMIN);
        admin.setId(id);
        admin.setAdminProfile(AdminProfile.builder().user(admin).build());

        when(userRepository.findById(id)).thenReturn(Optional.of(admin));

        assertThrows(ConflictException.class, () -> service.updateRole(id, UserRole.CUSTOMER));
    }

    @Test
    void aChangeBetweenTheTwoStaffRolesKeepsTheSameProfile() {

        UUID id = UUID.randomUUID();

        User employee = account(UserRole.EMPLOYEE);
        employee.setId(id);

        AdminProfile profile = AdminProfile.builder().user(employee).build();
        employee.setAdminProfile(profile);

        when(userRepository.findById(id)).thenReturn(Optional.of(employee));

        User updated = service.updateRole(id, UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, updated.getRole());

        // The same object, not an equivalent one: the employment record and the
        // module grants have to survive the promotion.
        assertSame(profile, updated.getAdminProfile());
    }

    @Test
    void aChangeToTheRoleAlreadyHeldDoesNothing() {

        UUID id = UUID.randomUUID();

        User admin = account(UserRole.ADMIN);
        admin.setId(id);
        admin.setAdminProfile(AdminProfile.builder().user(admin).build());

        when(userRepository.findById(id)).thenReturn(Optional.of(admin));

        User updated = service.updateRole(id, UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, updated.getRole());
        assertNotNull(updated.getAdminProfile());
    }

    // ------------------------------------------------------------------
    // Onboarding
    // ------------------------------------------------------------------

    @Test
    void onboardingIsRecordedOnTheCustomerProfile() {

        User customer = account(UserRole.CUSTOMER);
        customer.setCustomerProfile(CustomerProfile.builder().user(customer).build());

        service.updateOnboardingStep(customer, OnboardingStep.PAN_VERIFICATION);

        assertEquals(
                OnboardingStep.PAN_VERIFICATION,
                customer.getCustomerProfile().getOnboardingStep());
    }

    @Test
    void onboardingIsRefusedForAnAccountThatIsNotACustomer() {

        User admin = account(UserRole.ADMIN);
        admin.setAdminProfile(AdminProfile.builder().user(admin).build());

        assertThrows(
                IllegalStateException.class,
                () -> service.updateOnboardingStep(admin, OnboardingStep.PAN_VERIFICATION));
    }
}
