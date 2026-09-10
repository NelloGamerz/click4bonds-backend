package com.click4bonds.app.Modules.OTP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Config.RedisConfig;
import com.click4bonds.app.Modules.OTP.Model.OtpData;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins down the format OTP state is stored in.
 *
 * <p>Values are JSON. That is not cosmetic: the previous JDK-serialized format
 * stored only a class <em>name</em>, so the class a payload came back as
 * depended on whichever classloader happened to be current at read time — which
 * is how an entry could deserialize into a look-alike class that carried the
 * same name yet failed every identity check.</p>
 */
class SerializationTest {

    private static final Instant CREATED = Instant.parse("2026-09-10T10:00:00Z");

    private final ObjectMapper mapper = new RedisConfig().redisObjectMapper();

    private static OtpData sample(String hash) {
        return new OtpData(hash, "user@example.com", OtpType.EMAIL, CREATED, CREATED.plusSeconds(900), 0);
    }

    @Test
    void shouldRoundTripOtpDataThroughJson() throws Exception {

        OtpData original = new OtpData(
                "hash-value",
                "user@example.com",
                OtpType.EMAIL,
                CREATED,
                CREATED.plusSeconds(900),
                2);

        OtpData restored = mapper.readValue(mapper.writeValueAsString(original), OtpData.class);

        assertEquals(original.getOtpHash(), restored.getOtpHash());
        assertEquals(original.getIdentifier(), restored.getIdentifier());
        assertEquals(original.getOtpType(), restored.getOtpType());
        assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        assertEquals(original.getExpiresAt(), restored.getExpiresAt());
        assertEquals(original.getAttemptCount(), restored.getAttemptCount());
    }

    @Test
    void shouldRoundTripTheIncrementedAttemptCount() throws Exception {

        OtpData original = sample("hash");

        OtpData restored = mapper.readValue(
                mapper.writeValueAsString(original.withAttemptCount(4)), OtpData.class);

        assertEquals(4, restored.getAttemptCount());
        assertEquals(0, original.getAttemptCount(), "withAttemptCount must not mutate the receiver");
    }

    /**
     * The payload must be text a human can read in {@code redis-cli} — no JDK
     * stream magic header, no {@code java.io.*} class names.
     */
    @Test
    void shouldStoreReadableJsonRatherThanAJdkStream() throws Exception {

        byte[] encoded = mapper.writeValueAsString(sample("hash")).getBytes(StandardCharsets.UTF_8);

        assertFalse(
                encoded.length >= 2 && (encoded[0] & 0xFF) == 0xAC && (encoded[1] & 0xFF) == 0xED,
                "A JDK serialization stream would start with the AC ED magic header");

        String json = new String(encoded, StandardCharsets.UTF_8);

        assertFalse(json.contains("java.io"), "Native serialization markers must not appear");
        assertFalse(json.contains("java.lang"), "Native serialization markers must not appear");
        assertTrue(json.startsWith("{") && json.endsWith("}"), "The payload must be a JSON object");
    }

    /**
     * No polymorphic type metadata. A reader names the type it wants; the
     * payload never gets to choose which class is instantiated.
     */
    @Test
    void shouldNeverWriteTypeMetadataIntoThePayload() throws Exception {

        String json = mapper.writeValueAsString(sample("hash"));

        assertFalse(json.contains("@class"), "Type metadata would let the payload pick the class");
        assertFalse(json.contains("OtpData"), "The class name must not travel with the value");
    }

    /**
     * A property that disappears from the value object must not make every
     * already-stored entry unreadable.
     */
    @Test
    void shouldIgnorePropertiesItDoesNotKnow() throws Exception {

        OtpData restored = mapper.readValue(
                "{\"otpHash\":\"h\",\"identifier\":\"user@example.com\",\"otpType\":\"EMAIL\","
                        + "\"createdAt\":\"" + CREATED + "\",\"expiresAt\":\"" + CREATED.plusSeconds(900)
                        + "\",\"attemptCount\":1,\"somethingRetired\":true}",
                OtpData.class);

        assertEquals("h", restored.getOtpHash());
        assertEquals(1, restored.getAttemptCount());
    }

    @Test
    void shouldKeepTheIdentifierAndTypeDistinctAcrossChannels() throws Exception {

        OtpData email = new OtpData("h", "user@example.com", OtpType.EMAIL, CREATED, CREATED.plusSeconds(900), 0);
        OtpData sms = new OtpData("h", "user@example.com", OtpType.SMS, CREATED, CREATED.plusSeconds(900), 0);

        assertNotEquals(email.getOtpType(), sms.getOtpType());
        assertNotEquals(
                mapper.writeValueAsString(email),
                mapper.writeValueAsString(sms),
                "The channel must survive serialization");
    }

    /**
     * The serialized value must not contain the plaintext code anywhere —
     * only its hash may reach Redis.
     */
    @Test
    void shouldNotSerializeThePlaintextOtp() throws Exception {

        String json = mapper.writeValueAsString(sample("q1w2e3r4"));

        assertFalse(json.contains("483920"), "The code itself must never be part of the payload");
        assertTrue(json.contains("q1w2e3r4"), "Only the hash travels to Redis");
    }

    @Test
    void shouldNotExposeTheHashThroughToString() {

        assertFalse(sample("super-secret-hash").toString().contains("super-secret-hash"),
                "toString must never leak the hash into a log line");
    }

    @Test
    void shouldProduceStableJsonForTheSameState() throws Exception {

        assertEquals(
                mapper.writeValueAsString(sample("h")),
                mapper.writeValueAsString(sample("h")));
    }

}
