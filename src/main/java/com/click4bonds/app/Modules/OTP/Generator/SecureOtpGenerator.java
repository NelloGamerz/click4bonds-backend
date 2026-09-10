package com.click4bonds.app.Modules.OTP.Generator;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.OTP.Config.OtpProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Cryptographically secure OTP generator.
 *
 * <p>Each digit is drawn independently from {@link SecureRandom}, which rules
 * out the predictability of {@code Math.random()} and of generators seeded
 * from the clock. Because digits are appended individually and never parsed
 * back into a number, a leading zero survives: {@code 004821} is a valid,
 * six-digit result.</p>
 */
@Slf4j
@Component
public class SecureOtpGenerator implements OtpGenerator {

    private static final int DIGITS = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final OtpProperties properties;

    public SecureOtpGenerator(OtpProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generate() {

        int length = properties.getLength();

        if (length <= 0) {
            throw new IllegalStateException(
                    "otp.length must be a positive integer, but was " + length);
        }

        StringBuilder otp = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            otp.append(secureRandom.nextInt(DIGITS));
        }

        // Never log the code itself — only its shape.
        log.debug("Generated OTP of length {}", length);

        return otp.toString();
    }
}
