package com.click4bonds.app.Modules.Auth.Dto;

import com.click4bonds.app.Modules.User.Enums.AgeRange;
import com.click4bonds.app.Modules.User.Enums.CommunicationLanguage;
import com.click4bonds.app.Modules.User.Enums.UserType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The form a new account is opened with.
 *
 * <p>Sign-up is the one flow that collects a profile up front. Every other way
 * in — signing in with a number that already has an account — arrives with
 * nothing but the number, which is why these fields are nullable on the entity
 * but not here.</p>
 *
 * <p>The phone pattern only rejects values that could not be a number at all,
 * exactly as {@code SendPhoneOtpRequest} does; the authoritative check is the
 * OTP module's identifier normaliser, which also decides the canonical form the
 * account is stored under. The two must stay in step, or a number this accepts
 * would be refused when the code is issued.</p>
 *
 * <p>The three enums are Jackson's doing: a value outside the enum is rejected
 * before validation runs, so the caller is told the body could not be read
 * rather than which field was wrong. That is the price of not duplicating the
 * allowed values here, where they would drift from the enums they mirror.</p>
 *
 * <p>Two of the flags carry a second annotation on purpose.
 * {@code whatsappCommunicationConsent} is a consent, so it is a genuine yes/no
 * rather than a flag — {@code null} would be indistinguishable from a default,
 * and a consent never given must not be recorded as one.
 * {@code termsAccepted} has to be present <em>and</em> true, because
 * {@code @AssertTrue} passes on {@code null}: without the {@code @NotNull} a
 * body that simply omitted the field would open an account.</p>
 */
public record SignupRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z .'-]*$",
                message = "First name may contain letters, spaces, apostrophes, hyphens and periods only")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z .'-]*$",
                message = "Last name may contain letters, spaces, apostrophes, hyphens and periods only")
        String lastName,

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9][0-9\\s\\-().]{6,24}$",
                message = "Invalid phone number")
        String mobileNumber,

        @NotNull(message = "Age range is required")
        AgeRange ageRange,

        @NotNull(message = "User type is required")
        UserType userType,

        @NotNull(message = "Preferred communication language is required")
        CommunicationLanguage preferredCommunicationLanguage,

        @NotNull(message = "WhatsApp communication consent is required")
        Boolean whatsappCommunicationConsent,

        @NotNull(message = "Terms acceptance is required")
        @AssertTrue(message = "Terms and conditions must be accepted")
        Boolean termsAccepted) {
}
