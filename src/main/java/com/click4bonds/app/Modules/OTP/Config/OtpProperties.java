package com.click4bonds.app.Modules.OTP.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration of the OTP module.
 *
 * <pre>
 * otp:
 *   length: 6
 *   expiry-minutes: 15
 *   max-attempts: 5
 *   resend-cooldown-seconds: 60
 *   hash-secret: ${OTP_HASH_SECRET}
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    /** Number of digits in a generated OTP. */
    private int length = 6;

    /** How long an OTP stays valid, in minutes. Enforced by the Redis key TTL. */
    private int expiryMinutes = 15;

    /** Failed verifications tolerated before the OTP is destroyed. */
    private int maxAttempts = 5;

    /** Minimum delay between two OTP requests for the same type + identifier. */
    private int resendCooldownSeconds = 60;

    /**
     * Secret used to key the OTP hash. Supplied through the environment;
     * never committed to the repository.
     */
    private String hashSecret;
}
