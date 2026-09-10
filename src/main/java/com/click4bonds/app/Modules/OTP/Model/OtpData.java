package com.click4bonds.app.Modules.OTP.Model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

/**
 * State of a single pending OTP, stored in Redis.
 *
 * <p>The plaintext code is never part of this object: only its hash is kept
 * (see {@code OtpHasher}). No user information beyond the identifier the OTP
 * was requested for is stored.</p>
 *
 * <p>Stored as JSON, so the shape travels as field names and values rather
 * than as JVM class identity. The creator below names every property
 * explicitly: it pins the persisted format, and it keeps that format from
 * silently depending on whether the build was compiled with parameter names.
 * Adding a property is safe — readers ignore fields they do not know — but
 * renaming or removing one changes the stored contract, which is why the key
 * namespace carries a serialization version.</p>
 */
@Getter
public class OtpData {

    /** Hash of the expected code. */
    private final String otpHash;

    /** Normalized email address or phone number the OTP was issued for. */
    private final String identifier;

    /** Channel the OTP was issued for. */
    private final OtpType otpType;

    /** When the OTP was issued. */
    private final Instant createdAt;

    /** When the OTP stops being valid. Redis enforces this through the key TTL. */
    private final Instant expiresAt;

    /** Number of failed verification attempts so far. */
    private final int attemptCount;

    @JsonCreator
    public OtpData(
            @JsonProperty("otpHash") String otpHash,
            @JsonProperty("identifier") String identifier,
            @JsonProperty("otpType") OtpType otpType,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("expiresAt") Instant expiresAt,
            @JsonProperty("attemptCount") int attemptCount) {

        this.otpHash = otpHash;
        this.identifier = identifier;
        this.otpType = otpType;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attemptCount = attemptCount;
    }

    /**
     * @return a copy of this state with the attempt counter set to
     *         {@code newAttemptCount}
     */
    public OtpData withAttemptCount(int newAttemptCount) {
        return new OtpData(
                otpHash,
                identifier,
                otpType,
                createdAt,
                expiresAt,
                newAttemptCount);
    }

    /**
     * The hash is deliberately excluded from {@code toString()} so an OTP
     * state can never leak into a log line.
     */
    @Override
    public String toString() {
        return "OtpData{otpType=" + otpType
                + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt
                + ", attemptCount=" + attemptCount
                + '}';
    }
}
