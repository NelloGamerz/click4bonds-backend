package com.click4bonds.app.Modules.Auth.Model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A signed-in device, stored in Redis under its own session identifier.
 *
 * <p>A session is per-device, not per-user: the same account may hold several
 * at once and revoking one leaves the others alone. Nothing about the session
 * lives on the {@code User} row, so there is no single token to overwrite and
 * no way for one device's logout to sign the others out.</p>
 *
 * <p>Stored as JSON, so the shape travels as field names and values rather
 * than as JVM class identity. The creator names every property explicitly: it
 * pins the persisted format and keeps it from depending on whether the build
 * was compiled with parameter names.</p>
 *
 * <p>The session identifier itself is not a field. It is the Redis key, and
 * keeping it out of the value means a dumped value cannot be replayed as a
 * credential.</p>
 */
public record AuthSession(

        /** Owner of this session. */
        @JsonProperty("userId") UUID userId,

        /** When the device signed in. */
        @JsonProperty("createdAt") Instant createdAt,

        /** When the session stops being valid. Redis enforces this via the key TTL. */
        @JsonProperty("expiresAt") Instant expiresAt,

        /** When this session was last used to refresh. */
        @JsonProperty("lastUsedAt") Instant lastUsedAt,

        /**
         * Client description captured at sign-in, for an "active devices" view.
         * Free-form and never used for authorization.
         */
        @JsonProperty("userAgent") String userAgent) {

    @JsonCreator
    public AuthSession {
    }

    /** @return a copy of this session marked as used at {@code now} */
    public AuthSession usedAt(Instant now) {
        return new AuthSession(userId, createdAt, expiresAt, now, userAgent);
    }
}
