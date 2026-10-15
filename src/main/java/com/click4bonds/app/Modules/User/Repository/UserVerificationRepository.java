package com.click4bonds.app.Modules.User.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * Persistence for the per-channel verification records.
 *
 * <p>Keyed by {@link CustomerProfile} rather than by the account, because that
 * is what a verification belongs to: only a customer is asked to prove an email,
 * a phone, a PAN, a bank account or a demat account. Callers that hold an
 * account and need its record resolve the profile first — see
 * {@link com.click4bonds.app.Modules.User.Service.UserVerificationService},
 * which does it in one place so the ~15 callers in
 * {@link com.click4bonds.app.Modules.User.Service.VerificationService} did not
 * each have to learn the new shape.</p>
 */
public interface UserVerificationRepository
        extends JpaRepository<UserVerification, UUID> {

    Optional<UserVerification> findByCustomerProfile(CustomerProfile customerProfile);

    /**
     * Looks a record up by the account it belongs to.
     *
     * <p>A traversal, not a column: {@code customerProfile.user.id}. Kept
     * because the sign-in flows start from an authenticated identifier and have
     * no profile in hand.</p>
     */
    Optional<UserVerification> findByCustomerProfileUserId(UUID userId);
}
