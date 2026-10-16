package com.click4bonds.app.Modules.Admin.Dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Dto.UserVerificationResponse;
import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.OnboardingStep;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One account, as an administrator sees it: identity, the profile its role
 * implies, and everything an administrator may change about it.
 *
 * <p>Carries the staff fields as well as the customer ones, because the same
 * screen shows both and which half is populated follows from
 * {@link #role}. A customer has {@link #onboardingStep} and
 * {@link #verification} and no staff fields; a member of staff has
 * {@link #emailVerified} and {@link #accessConfig}, and no customer ones.</p>
 *
 * <p>This is what the update endpoint returns as well as what the details
 * endpoint returns, so a caller that changes something reads the result in the
 * same shape it read the account in — and can tell what actually changed without
 * a second request.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserDetailsResponse {

    private UUID id;
    private String email;
    private String mobileNumber;
    private String firstName;
    private String lastName;

    /**
     * The customer's onboarding step, or null for a staff account.
     *
     * <p>Onboarding is a customer lifecycle, so it comes from the customer
     * profile. A staff account has none and reports null rather than a step it
     * is not on.</p>
     */
    private OnboardingStep onboardingStep;

    private UserRole role;
    private UserStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    private UserVerificationResponse verification;

    /**
     * Whether the staff member has confirmed their address by signing in with
     * it. Null for a customer, who confirms channels on
     * {@link #verification} instead.
     */
    private Boolean emailVerified;

    /**
     * The modules a staff account may use. Null for a customer.
     *
     * <p>An empty set is meaningful and distinct from null: it says the account
     * is a member of staff who has been granted nothing. It constrains an
     * {@code EMPLOYEE}; an {@code ADMIN} is not limited by it at all.</p>
     */
    private Set<AdminModule> accessConfig;
}
