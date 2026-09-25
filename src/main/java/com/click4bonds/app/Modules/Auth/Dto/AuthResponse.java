package com.click4bonds.app.Modules.Auth.Dto;

import java.util.UUID;

/**
 * Result of a successful sign-in or refresh.
 *
 * <p>Carries the access token and the identifier of the account it was issued
 * to. That is the whole of it: a client needs a token to make requests and an
 * id to key its own state on, and anything beyond that is a payload it did not
 * ask for. The full profile lives at {@code GET /auth/me}, which a client can
 * call when it actually needs one — and which is the only place a verification
 * record is serialized.</p>
 *
 * <p>It deliberately does not carry the session identifier either. That travels
 * only in the HttpOnly cookie, where no script can reach it; returning it here
 * would undo the reason for using such a cookie. It would end up in JavaScript,
 * in a log, or in browser storage, and be replayable for the full session
 * lifetime rather than the minutes an access token lasts.</p>
 *
 * @param accessToken      bearer token for the {@code Authorization} header
 * @param tokenType        always {@code Bearer}
 * @param expiresInSeconds access token lifetime, so a client can refresh ahead
 *                         of expiry instead of being surprised by a 401
 * @param userId           the account that signed in, as {@code User.id}
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId) {

    public static final String BEARER = "Bearer";

    public AuthResponse(String accessToken, long expiresInSeconds, UUID userId) {
        this(accessToken, BEARER, expiresInSeconds, userId);
    }
}
