package com.click4bonds.app.Modules.User.Mapper;

import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Dto.UserVerificationResponse;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserVerification;

/**
 * Turns user entities into the shapes the API returns.
 *
 * <p>One place for the user projections, because they were not in one place
 * before. The verification record in particular was built field by field in both
 * {@code AuthService} and {@code AdminUserService}, from the same eight fields —
 * so a change to {@link UserVerification}, such as the demat channel that was
 * added recently, meant finding every builder and remembering to update it.</p>
 *
 * <p>Static and stateless: there is nothing to configure and nothing to inject,
 * and a mapper that has to be a bean is a mapper that has to be mocked in every
 * test that touches the service holding it.</p>
 *
 * <p>Kept in the {@code User} module rather than a shared one. Admin and Auth
 * both project a user, but the user is the User module's to describe; a mapper
 * living in {@code Admin} would have Auth depending on Admin, and one in a
 * neutral package would have neither module obviously owning it.</p>
 */
public final class UserMapper {

    private UserMapper() {
    }

    /**
     * The full profile returned by {@code GET /auth/me}.
     *
     * <p>The verification record is dropped once KYC is complete: every channel
     * in it reads {@code VERIFIED} by then, so it says nothing a caller can act
     * on. The field is left null and the DTO omits it from the JSON — see
     * {@link UserResponse#getVerification()} — rather than sending an object
     * full of identical values.</p>
     *
     * <p>Reads the id, name, contact details and role from an account. The
     * verification association is lazy, so a caller outside a transaction gets a
     * {@code LazyInitializationException} rather than a profile with the record
     * quietly missing; call this inside one.</p>
     *
     * @param user account to project
     * @return the profile, with the verification record included only while KYC
     *         is still outstanding
     */
    public static UserResponse toUserResponse(User user) {

        boolean kycCompleted = Boolean.TRUE.equals(user.getIsKycCompleted());

        UserVerification verification = kycCompleted ? null : user.getVerification();

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profileImage(user.getProfileImage())
                .onboardingStep(user.getOnboardingStep())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .isKycCompleted(user.getIsKycCompleted())
                .verification(toVerificationResponse(verification))
                .build();
    }

    /**
     * The per-channel verification record.
     *
     * <p>Accepts null and returns null, matching the nullable association it
     * comes from: an account that has not started verification has no record,
     * and both callers want the absent case to stay absent rather than become an
     * object of nulls.</p>
     *
     * @param verification record to project, or null
     * @return the projected record, or null if none was given
     */
    public static UserVerificationResponse toVerificationResponse(
            UserVerification verification) {

        if (verification == null) {
            return null;
        }

        return UserVerificationResponse.builder()
                .id(verification.getId())
                .emailStatus(verification.getEmailStatus())
                .phoneStatus(verification.getPhoneStatus())
                .panStatus(verification.getPanStatus())
                .bankAccountStatus(verification.getBankAccountStatus())
                .dematStatus(verification.getDematStatus())
                .createdAt(verification.getCreatedAt())
                .updatedAt(verification.getUpdatedAt())
                .build();
    }
}
