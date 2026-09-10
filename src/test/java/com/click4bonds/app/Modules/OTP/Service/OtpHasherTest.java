package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.OTP.Config.OtpProperties;

class OtpHasherTest {

    @Test
    void shouldNeverReturnTheCodeItself() {

        OtpHasher hasher = hasherWithSecret("a-secret");

        String hash = hasher.hash("483920");

        assertNotEquals("483920", hash);
        assertFalse(hash.contains("483920"));
    }

    @Test
    void shouldBeDeterministicForTheSameCode() {

        OtpHasher hasher = hasherWithSecret("a-secret");

        assertEquals(hasher.hash("483920"), hasher.hash("483920"));
    }

    @Test
    void shouldProduceDifferentHashesForDifferentCodes() {

        OtpHasher hasher = hasherWithSecret("a-secret");

        assertNotEquals(hasher.hash("483920"), hasher.hash("483921"));
    }

    /**
     * The key is what stops a stolen Redis dump from being brute-forced: the
     * same code must hash differently under a different secret.
     */
    @Test
    void shouldDependOnTheConfiguredSecret() {

        String first = hasherWithSecret("secret-one").hash("483920");
        String second = hasherWithSecret("secret-two").hash("483920");

        assertNotEquals(first, second);
    }

    @Test
    void shouldMatchOnlyTheCorrectCode() {

        OtpHasher hasher = hasherWithSecret("a-secret");
        String stored = hasher.hash("483920");

        assertTrue(hasher.matches("483920", stored));
        assertFalse(hasher.matches("483921", stored));
        assertFalse(hasher.matches("000000", stored));
    }

    @Test
    void shouldTreatUnusableInputAsANonMatch() {

        OtpHasher hasher = hasherWithSecret("a-secret");

        assertFalse(hasher.matches(null, hasher.hash("483920")));
        assertFalse(hasher.matches("483920", null));
    }

    @Test
    void shouldRefuseToStartWithoutASecret() {

        assertThrows(IllegalStateException.class,
                () -> new OtpHasher(propertiesWithSecret(null)));

        assertThrows(IllegalStateException.class,
                () -> new OtpHasher(propertiesWithSecret("   ")));
    }

    @Test
    void shouldStartWithAConfiguredSecret() {

        assertDoesNotThrow(() -> hasherWithSecret("a-secret"));
    }

    private OtpHasher hasherWithSecret(String secret) {
        return new OtpHasher(propertiesWithSecret(secret));
    }

    private OtpProperties propertiesWithSecret(String secret) {

        OtpProperties properties = new OtpProperties();
        properties.setHashSecret(secret);

        return properties;
    }
}
