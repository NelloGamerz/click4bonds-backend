package com.click4bonds.app.Modules.OTP.Generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.OTP.Config.OtpProperties;

class SecureOtpGeneratorTest {

    private static final int SAMPLES = 10_000;

    @Test
    void shouldGenerateExactlySixDigitsByDefault() {

        SecureOtpGenerator generator = generatorWith(new OtpProperties());

        assertEquals("6", String.valueOf(generator.generate().length()));
    }

    @Test
    void shouldGenerateOnlyDigits() {

        SecureOtpGenerator generator = generatorWith(new OtpProperties());

        for (int i = 0; i < 1_000; i++) {
            assertTrue(generator.generate().matches("[0-9]{6}"),
                    "Every OTP must be six numeric characters");
        }
    }

    @Test
    void shouldHonourConfiguredLength() {

        OtpProperties properties = new OtpProperties();
        properties.setLength(8);

        SecureOtpGenerator generator = generatorWith(properties);

        for (int i = 0; i < 100; i++) {
            assertTrue(generator.generate().matches("[0-9]{8}"));
        }
    }

    /**
     * A generator that builds the code numerically (or via {@code %06d} on an
     * int that was never left-padded) would never emit a leading zero. Over
     * 10 000 draws a genuine per-digit generator misses one with probability
     * 0.9^10000 — effectively zero.
     */
    @Test
    void shouldAllowLeadingZeroes() {

        SecureOtpGenerator generator = generatorWith(new OtpProperties());

        boolean sawLeadingZero = false;

        for (int i = 0; i < SAMPLES && !sawLeadingZero; i++) {
            sawLeadingZero = generator.generate().startsWith("0");
        }

        assertTrue(sawLeadingZero,
                "Leading zeroes must survive generation — the code must not round-trip through an int");
    }

    @Test
    void shouldNotRepeatItselfAcrossCalls() {

        SecureOtpGenerator generator = generatorWith(new OtpProperties());

        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            generated.add(generator.generate());
        }

        assertNotEquals(1, generated.size(),
                "A constant or predictable sequence is not acceptable");
        assertTrue(generated.size() > 400,
                "Codes should be well spread out, but only " + generated.size() + " of 500 were distinct");
    }

    @Test
    void shouldRejectNonPositiveConfiguredLength() {

        OtpProperties properties = new OtpProperties();
        properties.setLength(0);

        SecureOtpGenerator generator = generatorWith(properties);

        assertThrows(IllegalStateException.class, generator::generate);
    }

    /**
     * Guards the "no Math.random()" requirement structurally: the generator
     * must hold a {@link SecureRandom}.
     */
    @Test
    void shouldUseASecureRandomSource() {

        boolean hasSecureRandom = false;

        for (Field field : SecureOtpGenerator.class.getDeclaredFields()) {
            if (SecureRandom.class.isAssignableFrom(field.getType())) {
                hasSecureRandom = true;
            }
        }

        assertTrue(hasSecureRandom,
                "SecureOtpGenerator must draw from java.security.SecureRandom");
    }

    private SecureOtpGenerator generatorWith(OtpProperties properties) {
        return new SecureOtpGenerator(properties);
    }
}
