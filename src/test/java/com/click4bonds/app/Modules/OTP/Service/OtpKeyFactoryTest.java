package com.click4bonds.app.Modules.OTP.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * Locks the Redis key layout down. Anything that changes these strings
 * silently invalidates every outstanding OTP, so they are asserted literally
 * here — and only here; every other test derives its keys from the factory.
 */
class OtpKeyFactoryTest {

    @Test
    void shouldBuildTheEmailOtpKey() {

        assertEquals(
                "otp:v2:email:user@example.com",
                OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com"));
    }

    @Test
    void shouldBuildTheSmsOtpKey() {

        assertEquals(
                "otp:v2:sms:+919876543210",
                OtpKeyFactory.otpKey(OtpType.SMS, "+919876543210"));
    }

    @Test
    void shouldBuildTheEmailCooldownKey() {

        assertEquals(
                "otp:v2:cooldown:email:user@example.com",
                OtpKeyFactory.cooldownKey(OtpType.EMAIL, "user@example.com"));
    }

    @Test
    void shouldBuildTheSmsCooldownKey() {

        assertEquals(
                "otp:v2:cooldown:sms:+919876543210",
                OtpKeyFactory.cooldownKey(OtpType.SMS, "+919876543210"));
    }

    /**
     * The reason the namespace carries a version at all: entries written before
     * values became JSON hold JDK-serialized streams. Nothing may read them, so
     * no key the factory builds may sit in the pre-JSON namespace.
     */
    @Test
    void shouldNeverBuildAKeyInTheRetiredNamespace() {

        assertFalse(
                OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com").startsWith("otp:email:"),
                "otp:email:* holds pre-JSON entries and must never be addressed again");

        assertFalse(
                OtpKeyFactory.cooldownKey(OtpType.EMAIL, "user@example.com")
                        .startsWith("otp:cooldown:"),
                "otp:cooldown:* holds pre-JSON entries and must never be addressed again");
    }

    @Test
    void shouldNeverCollideAcrossChannels() {

        assertNotEquals(
                OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com"),
                OtpKeyFactory.otpKey(OtpType.SMS, "user@example.com"));
    }

    @Test
    void shouldNeverCollideBetweenOtpAndCooldownKeys() {

        assertNotEquals(
                OtpKeyFactory.otpKey(OtpType.EMAIL, "user@example.com"),
                OtpKeyFactory.cooldownKey(OtpType.EMAIL, "user@example.com"));
    }
}
