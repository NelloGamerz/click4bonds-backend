package com.click4bonds.app.Modules.User.Enums;

/**
 * The order in which an account is asked to prove who it is.
 *
 * <p>Phone comes first because a phone number is what an account is created
 * with: sign-up collects the number and asks for its code before anything else,
 * so the step it opens on is the phone step. The address is acquired later, at
 * the email step, and the identifiers the platform cannot check by sending a
 * code — PAN, bank account and demat — follow.</p>
 *
 * <p>The declaration order is the order onboarding advances in. It is read as
 * such by {@code VerificationService#successor}, so moving a constant moves the
 * step for real.</p>
 */
public enum OnboardingStep {

    PHONE_VERIFICATION,

    EMAIL_VERIFICATION,

    PAN_VERIFICATION,

    BANK_ACCOUNT_VERIFICATION,

    DEMAT_VERIFICATION,

    COMPLETED
}
