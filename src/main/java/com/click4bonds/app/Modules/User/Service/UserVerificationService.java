package com.click4bonds.app.Modules.User.Service;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.User.Enums.VerificationStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;
import com.click4bonds.app.Modules.User.Repository.UserVerificationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserVerificationService {

    private final UserVerificationRepository userVerificationRepository;

    /**
     * Creates the initial verification record for a newly created user.
     *
     * All verification statuses start as NOT_STARTED.
     */
    public UserVerification createVerification(User user) {

        UserVerification verification = UserVerification.builder()
                .user(user)
                .emailStatus(VerificationStatus.NOT_STARTED)
                .phoneStatus(VerificationStatus.NOT_STARTED)
                .panStatus(VerificationStatus.NOT_STARTED)
                .bankAccountStatus(VerificationStatus.NOT_STARTED)
                .build();

        UserVerification saved = userVerificationRepository.save(verification);

        log.info("Created verification record for user {}", user.getId());

        return saved;
    }

    public UserVerification getVerification(User user) {

        return userVerificationRepository.findByUser(user)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Verification record not found for user " + user.getId()
                        ));
    }

    public UserVerification getVerificationByUserId(java.util.UUID userId) {

        return userVerificationRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Verification record not found for user " + userId
                        ));
    }

    public void updateEmailStatus(
            User user,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(user);

        verification.setEmailStatus(status);

        log.info(
                "Updated email verification status for user {} to {}",
                user.getId(),
                status
        );
    }

    public void updatePhoneStatus(
            User user,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(user);

        verification.setPhoneStatus(status);

        log.info(
                "Updated phone verification status for user {} to {}",
                user.getId(),
                status
        );
    }

    public void updatePanStatus(
            User user,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(user);

        verification.setPanStatus(status);

        log.info(
                "Updated PAN verification status for user {} to {}",
                user.getId(),
                status
        );
    }

    public void updateBankAccountStatus(
            User user,
            VerificationStatus status
    ) {

        UserVerification verification = getVerification(user);

        verification.setBankAccountStatus(status);

        log.info(
                "Updated bank account verification status for user {} to {}",
                user.getId(),
                status
        );
    }
}
