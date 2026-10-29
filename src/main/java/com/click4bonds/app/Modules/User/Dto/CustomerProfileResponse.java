package com.click4bonds.app.Modules.User.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.AgeRange;
import com.click4bonds.app.Modules.User.Enums.CommunicationLanguage;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserType;
import com.click4bonds.app.Modules.User.Model.CustomerProfile;

/**
 * The customer half of an account, returned by {@code GET /auth/me}.
 *
 * <p>Null for a staff account, which has no customer profile.</p>
 */
public record CustomerProfileResponse(
        UUID id,
        UUID partyId,
        OnboardingStep onboardingStep,
        Boolean isKycCompleted,
        AgeRange ageRange,
        UserType userType,
        CommunicationLanguage preferredCommunicationLanguage,
        Boolean whatsappCommunicationConsent,
        Boolean termsAccepted,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerProfileResponse from(CustomerProfile profile) {

        return new CustomerProfileResponse(
                profile.getId(),
                profile.getParty() != null ? profile.getParty().getId() : null,
                profile.getOnboardingStep(),
                profile.getIsKycCompleted(),
                profile.getAgeRange(),
                profile.getUserType(),
                profile.getPreferredCommunicationLanguage(),
                profile.getWhatsappCommunicationConsent(),
                profile.getTermsAccepted(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
