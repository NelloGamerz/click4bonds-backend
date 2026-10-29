package com.click4bonds.app.Modules.User.Dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Model.AdminProfile;

/**
 * The staff half of an account, returned by {@code GET /auth/me}.
 *
 * <p>Null for a customer, which has no admin profile.</p>
 */
public record AdminProfileResponse(
        UUID id,
        Boolean emailVerified,
        Set<AdminModule> accessConfig,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminProfileResponse from(AdminProfile profile) {

        return new AdminProfileResponse(
                profile.getId(),
                profile.getEmailVerified(),
                profile.getAccessConfig(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
