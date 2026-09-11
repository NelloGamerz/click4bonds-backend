package com.click4bonds.app.Modules.User.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder
public class UserResponse {

    private UUID id;
    private String clerkUserId;
    private String email;
    private String mobileNumber;
    private String firstName;
    private String lastName;
    private String profileImage;

    private OnboardingStep onboardingStep;
    private UserRole role;
    private UserStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    private UserVerificationResponse verification;
}
