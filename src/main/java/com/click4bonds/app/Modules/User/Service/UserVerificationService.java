package com.click4bonds.app.Modules.User.Service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.UserVerification;
import com.click4bonds.app.Modules.User.Repository.UserVerificationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persistence for the per-channel verification record.
 *
 * <p>Keyed by {@link CustomerProfile}, not by the account: a verification record
 * only ever belongs to a customer. Callers that hold an account resolve the
 * profile first — {@link VerificationService} does it once per entry point —
 * rather than this service reaching back through the account, so the rule that
 * only customers verify is stated where the caller decides to verify something,
 * not hidden in a lookup that could have silently returned nothing.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserVerificationService {

    private final UserVerificationRepository userVerificationRepository;

    /**
     * Creates the initial verification record for a newly created customer.
     *
     * All verification statuses start as NOT_STARTED.
     */
    public UserVerification createVerification(CustomerProfile customerProfile) {

        UserVerification verification = UserVerification.builder()
                .customerProfile(customerProfile)
                .emailStatus(VerificationStatus.NOT_STARTED)
                .phoneStatus(VerificationStatus.NOT_STARTED)
                .panStatus(VerificationStatus.NOT_STARTED)
                .bankAccountStatus(VerificationStatus.NOT_STARTED)
                .dematStatus(VerificationStatus.NOT_STARTED)
                .build();

        UserVerification saved = userVerificationRepository.save(verification);

        log.info("Created verification record for user {}", customerProfile.getUser().getId());

        return saved;
    }

    public UserVerification getVerification(CustomerProfile customerProfile) {

        return userVerificationRepository.findByCustomerProfile(customerProfile)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Verification record not found for user "
                                        + customerProfile.getUser().getId()
                        ));
    }

    public UserVerification getVerificationByCustomerProfileId(UUID customerProfileId) {

        return userVerificationRepository.findByCustomerProfileUserId(customerProfileId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Verification record not found for customer profile "
                                        + customerProfileId
                        ));
    }

    public void updateEmailStatus(
            CustomerProfile customerProfile,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(customerProfile);

        verification.setEmailStatus(status);

        log.info(
                "Updated email verification status for user {} to {}",
                customerProfile.getUser().getId(),
                status
        );
    }

    public void updatePhoneStatus(
            CustomerProfile customerProfile,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(customerProfile);

        verification.setPhoneStatus(status);

        log.info(
                "Updated phone verification status for user {} to {}",
                customerProfile.getUser().getId(),
                status
        );
    }

    public void updatePanStatus(
            CustomerProfile customerProfile,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(customerProfile);

        verification.setPanStatus(status);

        log.info(
                "Updated PAN verification status for user {} to {}",
                customerProfile.getUser().getId(),
                status
        );
    }

    public void updateBankAccountStatus(
            CustomerProfile customerProfile,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(customerProfile);

        verification.setBankAccountStatus(status);

        log.info(
                "Updated bank account verification status for user {} to {}",
                customerProfile.getUser().getId(),
                status
        );
    }

    public void updateDematStatus(
            CustomerProfile customerProfile,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(customerProfile);

        verification.setDematStatus(status);

        log.info(
                "Updated demat verification status for user {} to {}",
                customerProfile.getUser().getId(),
                status
        );
    }
}
