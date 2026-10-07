package com.click4bonds.app.Modules.Admin.Mapper;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.User.Mapper.UserMapper;
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
     * <p>Deliberately does not touch {@link User#getVerification()}. That
     * association is lazy, and this mapper runs once per row of a page, so
     * reading it here would turn one query into one query per row. The KYC flag
     * is a column on the account and costs nothing.</p>
     *
     * @param user account to project
     * @return the listing row
     */
    public static AdminUserSummaryResponse toSummary(User user) {

        return AdminUserSummaryResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getMobileNumber())
                .isKycCompleted(user.getIsKycCompleted())
                .role(user.getRole())
                .status(user.getStatus())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * The full account shown on the admin detail screen, verification included.
     *
     * <p>Unlike the listing, this one is a single account and reads the lazy
     * verification association, so it has to be called inside a transaction.</p>
     *
     * @param user account to project
     * @return the detail view
     */
    public static AdminUserDetailsResponse toDetails(User user) {

        return AdminUserDetailsResponse.builder()
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
                .verification(UserMapper.toVerificationResponse(user.getVerification()))
                .build();
    }
}
