package com.click4bonds.app.Modules.Common.Dto;

import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.UserRole;

public record UserRoleChangedEvent(
        UUID userId,
        String clerkUserId,
        UserRole role
) {}
