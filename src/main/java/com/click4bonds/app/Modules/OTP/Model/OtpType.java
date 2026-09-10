package com.click4bonds.app.Modules.OTP.Model;

/**
 * Delivery channel an OTP belongs to.
 *
 * <p>The type is part of the Redis key, so an OTP issued for a phone number
 * can never be redeemed for the email address that happens to share the same
 * identifier value — and vice versa. Email and SMS flows are fully
 * independent: generating one never invalidates the other.</p>
 */
public enum OtpType {
    EMAIL,
    SMS
}
