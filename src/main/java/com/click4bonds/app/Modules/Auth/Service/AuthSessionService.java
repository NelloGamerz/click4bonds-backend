package com.click4bonds.app.Modules.Auth.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Auth.Model.AuthSession;
import com.click4bonds.app.Modules.Common.Redis.RedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps server-side login sessions in Redis.
 *
 * <p>A session identifier is 256 bits from {@link SecureRandom}, base64url
 * encoded. It is not derived from the access token and carries no information:
 * it is a lookup key and nothing more, so it cannot be forged, guessed, or
 * decoded into anything useful. Only its hash-free opaque value ever leaves
 * the server, and only inside an HttpOnly cookie.</p>
 *
 * <p>Sessions are per-device. The account is not modified when one is created
 * or destroyed, so any number of devices may be signed in at once and revoking
 * one leaves the rest untouched.</p>
 *
 * <h2>Rotation and the multi-tab race</h2>
 *
 * <p>Refreshing normally rotates the identifier, so a stolen cookie is only
 * useful until the legitimate client next refreshes. The obvious hazard is two
 * tabs refreshing at the same moment: the first rotates, and the second then
 * presents an identifier that no longer exists.</p>
 *
 * <p>A rotating identifier therefore leaves a short-lived pointer behind it, and
 * resolution follows that pointer. Two rules keep this from becoming a chain of
 * redirects:</p>
 *
 * <ol>
 *   <li>Rotation happens only when the presented identifier was itself the
 *       current one. A request that arrived by following a pointer extends the
 *       session it found instead of minting another identifier.</li>
 *   <li>Every response carries the current identifier back to the client, so
 *       the lagging tab is corrected on the spot.</li>
 * </ol>
 *
 * <p>Both tabs therefore converge on one identifier within a single refresh
 * each, instead of drifting apart or invalidating one another.</p>
 */
@Slf4j
@Service
public class AuthSessionService {

    /**
     * How long a rotated-out identifier keeps resolving to its replacement.
     *
     * <p>This only has to outlive the gap between two tabs firing at once, so
     * it is a small constant rather than a policy knob. It is not a way to keep
     * using an old identifier: past this window the old value is simply gone.</p>
     */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(60);

    private static final int SESSION_ID_BYTES = 32;

    private final RedisService redisService;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(RedisService redisService, AuthProperties properties) {
        this.redisService = redisService;
        this.properties = properties;
    }

    /** A session and the identifier that currently addresses it. */
    public record ResolvedSession(String sessionId, AuthSession session) {
    }

    /**
     * Opens a new session for a device.
     *
     * @param userId    account signing in
     * @param userAgent client description, stored for an active-devices view
     * @return the identifier to hand back in the cookie
     */
    public String create(UUID userId, String userAgent) {

        String sessionId = newSessionId();
        Instant now = Instant.now();

        AuthSession session = new AuthSession(
                userId,
                now,
                now.plus(properties.getSession().getTtl()),
                now,
                userAgent);

        redisService.set(
                AuthKeyFactory.sessionKey(sessionId),
                session,
                properties.getSession().getTtl());

        log.info("Session opened for user {}", userId);

        return sessionId;
    }

    /**
     * Resolves a presented identifier and, when appropriate, rotates it.
     *
     * <p>See the class comment for the rotation rules. The returned identifier
     * is always the one the cookie should be set to, which may differ from the
     * one presented.</p>
     *
     * @param presentedSessionId identifier from the request cookie
     * @param userAgent          client description, refreshed on each rotation
     * @return the live session and its current identifier, or empty when the
     *         identifier is unknown, expired or revoked
     */
    public Optional<ResolvedSession> refresh(String presentedSessionId, String userAgent) {

        Optional<ResolvedSession> resolved = resolve(presentedSessionId);

        if (resolved.isEmpty()) {
            return Optional.empty();
        }

        ResolvedSession current = resolved.get();

        // Arrived by following a pointer: this is the lagging half of a race,
        // so extend what was found rather than rotating out from under the tab
        // that just rotated.
        if (!current.sessionId().equals(presentedSessionId)) {
            return Optional.of(extend(current));
        }

        return Optional.of(rotate(current, userAgent));
    }

    /**
     * Resolves an identifier to a live session without rotating anything.
     *
     * @param presentedSessionId identifier from the request cookie
     * @return the live session and its current identifier, if any
     */
    public Optional<ResolvedSession> resolve(String presentedSessionId) {

        if (presentedSessionId == null || presentedSessionId.isBlank()) {
            return Optional.empty();
        }

        Optional<AuthSession> direct = read(presentedSessionId);

        if (direct.isPresent()) {
            return Optional.of(new ResolvedSession(presentedSessionId, direct.get()));
        }

        // Not current: it may have been rotated moments ago by another request.
        Optional<String> replacement = redisService.get(
                AuthKeyFactory.graceKey(presentedSessionId),
                String.class);

        if (replacement.isEmpty()) {
            return Optional.empty();
        }

        return read(replacement.get())
                .map(session -> new ResolvedSession(replacement.get(), session));
    }

    /**
     * Ends a session.
     *
     * <p>Idempotent: revoking something that is already gone is not an error,
     * because a logout that raced the session's own expiry must still succeed.
     * Only the named session is touched, so one device signing out never
     * disturbs another.</p>
     *
     * @param sessionId identifier from the request cookie
     */
    public void revoke(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        redisService.delete(AuthKeyFactory.sessionKey(sessionId));
        redisService.delete(AuthKeyFactory.graceKey(sessionId));

        log.info("Session revoked");
    }

    /**
     * Reads a session, treating a Redis entry that outlived its own expiry as
     * absent.
     *
     * <p>Redis is what actually expires these; this guards the read that raced
     * the TTL, so a session can never be accepted on the strength of a stale
     * value that Redis has not yet collected.</p>
     */
    private Optional<AuthSession> read(String sessionId) {

        Optional<AuthSession> found = redisService.get(
                AuthKeyFactory.sessionKey(sessionId),
                AuthSession.class);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        AuthSession session = found.get();

        if (Instant.now().isAfter(session.expiresAt())) {
            redisService.delete(AuthKeyFactory.sessionKey(sessionId));
            return Optional.empty();
        }

        return found;
    }

    /**
     * Issues a fresh identifier for a session and points the old one at it.
     *
     * <p>{@code createdAt} is carried across: rotating is a continuation of the
     * same sign-in, not a new one, so "signed in since" must not keep sliding
     * forward. The expiry does slide, which is what keeps an active device
     * signed in and eventually retires an idle one.</p>
     */
    private ResolvedSession rotate(ResolvedSession current, String userAgent) {

        String newSessionId = newSessionId();
        Instant now = Instant.now();

        AuthSession rotated = new AuthSession(
                current.session().userId(),
                current.session().createdAt(),
                now.plus(properties.getSession().getTtl()),
                now,
                userAgent != null ? userAgent : current.session().userAgent());

        redisService.set(
                AuthKeyFactory.sessionKey(newSessionId),
                rotated,
                properties.getSession().getTtl());

        // Left behind so a simultaneous request holding the old identifier is
        // answered instead of rejected. Short-lived by construction.
        redisService.set(
                AuthKeyFactory.graceKey(current.sessionId()),
                newSessionId,
                ROTATION_GRACE);

        redisService.delete(AuthKeyFactory.sessionKey(current.sessionId()));

        return new ResolvedSession(newSessionId, rotated);
    }

    /** Pushes a session's expiry out without changing its identifier. */
    private ResolvedSession extend(ResolvedSession current) {

        Instant now = Instant.now();

        AuthSession extended = new AuthSession(
                current.session().userId(),
                current.session().createdAt(),
                now.plus(properties.getSession().getTtl()),
                now,
                current.session().userAgent());

        redisService.set(
                AuthKeyFactory.sessionKey(current.sessionId()),
                extended,
                properties.getSession().getTtl());

        return new ResolvedSession(current.sessionId(), extended);
    }

    /**
     * @return 256 bits of randomness, base64url encoded without padding so the
     *         value is safe to put in a cookie verbatim
     */
    private String newSessionId() {

        byte[] bytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
