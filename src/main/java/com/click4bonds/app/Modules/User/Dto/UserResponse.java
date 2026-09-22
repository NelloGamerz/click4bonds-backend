package com.click4bonds.app.Modules.User.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's full profile.
 *
 * <p>Returned by {@code GET /auth/me}. Not by sign-in or refresh: those answer
 * with an access token and the account's identifier, so a client that only
 * needs to start making requests is not made to carry a profile — and a
 * verification record — it did not ask for.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
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

    private Boolean isKycCompleted;

    /**
     * The per-channel verification record, present only while the account is
     * still working through KYC.
     *
     * <p>Omitted entirely — not serialized as {@code null} — once
     * {@link #isKycCompleted} is true. Every channel in it reads
     * {@code VERIFIED} at that point, so the object carries no information a
     * caller can act on, and leaving it out says "there is nothing outstanding
     * here" more directly than four identical fields would.</p>
     *
     * <p>{@code NON_NULL} is set on this field alone rather than on the class:
     * an account that signed up by phone has a null {@code email}, and that
     * field must still be present in the JSON so a client can tell "no address
     * yet" from "this API does not return addresses".</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UserVerificationResponse verification;
}
