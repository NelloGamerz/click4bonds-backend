package com.click4bonds.app.Modules.User.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * What signing in records, for an account that is not a customer.
 *
 * <p>The sign-in flows call these methods for whichever account signed in, so
 * they have to cope with a staff account. Before the profile split every account
 * had a verification record and there was nothing to cope with; now only a
 * customer has one, and a staff account that signed in by email would otherwise
 * be refused by a lookup that had nothing to find.</p>
 */
class VerificationServiceStaffAccountTest {

    private final UserVerificationService userVerificationService =
            mock(UserVerificationService.class);

    /**
     * Only the verification service is real enough to matter here. The remaining
     * collaborators are not reached by the paths under test: a staff account has
     * no OTP, no email and no onboarding to advance.
     */
    private final VerificationService service =
            new VerificationService(null, null, null, userVerificationService, null, null);

    private static User admin() {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("staff@example.com")
                .role(UserRole.ADMIN)
                .build();

        user.setAdminProfile(AdminProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .build());

        return user;
    }

    @Test
    void signingInByEmailConfirmsAStaffAccountsAddress() {

        User admin = admin();

        assertFalse(admin.getAdminProfile().getEmailVerified());

        service.markEmailVerified(admin);

        // The address was named by an administrator and nobody had proven the
        // staff member could receive mail at it. Redeeming a code sent there is
        // that proof.
        assertTrue(admin.getAdminProfile().getEmailVerified());
    }

    @Test
    void signingInByPhoneIsIgnoredForAStaffAccountRatherThanRefused() {

        User admin = admin();

        // Phone verification is a customer KYC channel. A staff account has
        // none, but signing in with a proven number is still a valid sign-in,
        // so this records nothing rather than failing the request.
        service.markPhoneVerified(admin);

        assertFalse(admin.getAdminProfile().getEmailVerified());
    }

    @Test
    void aCustomerStillRecordsItsEmailAgainstTheVerificationRecord() {

        User customer = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .build();

        // Deliberately past the email step, so advancing onward is not part of
        // what this asserts — that rule is covered by VerificationServiceEmailTest.
        CustomerProfile profile = CustomerProfile.builder()
                .id(UUID.randomUUID())
                .user(customer)
                .onboardingStep(OnboardingStep.PAN_VERIFICATION)
                .build();

        customer.setCustomerProfile(profile);

        service.markEmailVerified(customer);

        // The customer path is unchanged: the channel is marked on the record.
        verify(userVerificationService)
                .updateEmailStatus(profile, VerificationStatus.VERIFIED);
    }
}
