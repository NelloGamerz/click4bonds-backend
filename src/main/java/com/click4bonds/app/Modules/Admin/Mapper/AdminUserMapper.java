package com.click4bonds.app.Modules.Admin.Mapper;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.User.Mapper.UserMapper;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * Turns user entities into the shapes the admin endpoints return.
 *
 * <p>Two projections rather than one, because the admin screens need different
 * amounts of an account. The listing shows a row per user and takes only what a
 * row can display; the detail view takes the profile and the verification
 * record. They are separate methods so that the listing cannot accidentally
 * start loading verification records it does not show.</p>
 *
 * <p>Everything customer-specific — KYC state, the onboarding step, the
 * verification record — is read through
 * {@link com.click4bonds.app.Modules.User.Model.CustomerProfile}, which is where
 * it moved. A staff account has no profile, so those fields come back null (or
 * false, for the listing's primitive flag) for one, which is what the admin
 * screens already showed for a staff row.</p>
 *
 * <p>The verification record itself is projected by {@link UserMapper}, which is
 * also what {@code AuthService} uses — the shape is the API's, not the admin
 * screen's, and both callers return the same object for it.</p>
 */
public final class AdminUserMapper {

    private AdminUserMapper() {
    }

    /**
     * The row shown in the customer list.
     *
     * <p>Deliberately does not touch the verification association. That
     * association is lazy, and this mapper runs once per row of a page, so
     * reading it here would turn one query into one query per row. The KYC flag
     * is a column on the profile and costs nothing — and
     * {@code UserRepository.findUsers} fetches the profile with the page, so
     * reading it does not cost a query per row either.</p>
     *
     * @param user account to project
     * @return the listing row
     */
    public static AdminUserSummaryResponse toSummary(User user) {

        CustomerProfile profile = user.getCustomerProfile();

        return AdminUserSummaryResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getMobileNumber())
                .isKycCompleted(profile != null && Boolean.TRUE.equals(profile.getIsKycCompleted()))
                .role(user.getRole())
                .status(user.getStatus())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * The full account shown on the admin detail screen, with the profile its
     * role implies.
     *
     * <p>Unlike the listing, this one is a single account and reads the lazy
     * profile and verification associations, so it has to be called inside a
     * transaction.</p>
     *
     * <p>Exactly one of the two profiles is populated, so exactly one half of
     * the response is: a customer's onboarding step and verification record, or
     * a staff account's confirmation state and grants. The other half comes back
     * null, which is what tells the caller which kind of account this is beyond
     * the role alone.</p>
     *
     * @param user account to project
     * @return the detail view
     */
    public static AdminUserDetailsResponse toDetails(User user) {

        CustomerProfile profile = user.getCustomerProfile();
        AdminProfile staffProfile = user.getAdminProfile();

        return AdminUserDetailsResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .onboardingStep(profile == null ? null : profile.getOnboardingStep())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .partyId(profile == null || profile.getParty() == null
                        ? null
                        : profile.getParty().getId())
                .verification(UserMapper.toVerificationResponse(
                        profile == null ? null : profile.getVerification()))
                .emailVerified(staffProfile == null ? null : staffProfile.getEmailVerified())
                .accessConfig(staffProfile == null ? null : staffProfile.getAccessConfig())
                .build();
    }
}
