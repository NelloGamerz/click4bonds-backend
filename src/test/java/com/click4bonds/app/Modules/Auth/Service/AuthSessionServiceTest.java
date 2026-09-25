package com.click4bonds.app.Modules.Auth.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Auth.AuthTestSupport;
import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;

/**
 * The Redis-backed sessions behind the refresh cookie.
 *
 * <p>Time is driven forward through the Redis stub rather than by sleeping, so
 * expiry and rotation can be tested exactly.</p>
 */
class AuthSessionServiceTest {

    private static final UUID USER_ID = UUID.fromString(AuthTestSupport.USER_ID);

    private InMemoryRedisService redis;
    private AuthProperties properties;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {

        redis = new InMemoryRedisService();
        properties = AuthTestSupport.properties();
        service = new AuthSessionService(redis, properties);
    }

    /**
     * Asserts a key's time to live is the configured lifetime.
     *
     * <p>Deliberately not an equality check. The store derives the remaining TTL
     * from the wall clock when it is read, so real time has always passed since
     * the key was written and the value can land a second low — which makes an
     * exact assertion pass or fail depending on how busy the machine is. What
     * matters here is the configured lifetime, not sub-second precision.</p>
     */
    private void assertTtlIs(Duration expected, String key) {

        long actual = redis.getTtl(key);

        assertTrue(
                Math.abs(expected.toSeconds() - actual) <= Duration.ofSeconds(5).toSeconds(),
                "expected a TTL near " + expected.toSeconds() + "s but was " + actual + "s");
    }

    @Test
    void sessionIsCreatedUnderItsOwnKeyWithATtl() {

        String sessionId = service.create(USER_ID, "test-agent");

        String key = AuthKeyFactory.sessionKey(sessionId);

        assertTrue(redis.exists(key));
        assertTtlIs(properties.getSession().getTtl(), key);
    }

    @Test
    void sessionIdentifierCarriesNoInformation() {

        String sessionId = service.create(USER_ID, "test-agent");

        // Not derived from the user, not sequential, not guessable by shape.
        assertFalse(sessionId.contains(USER_ID.toString()));

        String another = service.create(USER_ID, "test-agent");
        assertNotEquals(sessionId, another);
    }

    @Test
    void storedSessionDoesNotContainItsOwnIdentifier() {

        String sessionId = service.create(USER_ID, "test-agent");

        String stored = redis.get(AuthKeyFactory.sessionKey(sessionId), Object.class)
                .map(Object::toString)
                .orElseThrow();

        // The identifier is the key, never the value: a dumped value must not
        // be replayable as a credential.
        assertFalse(stored.contains(sessionId.replace("\"", "")));
    }

    @Test
    void sessionResolvesToItsOwnerAndCapturesTheUserAgent() {

        String sessionId = service.create(USER_ID, "test-agent");

        AuthSessionService.ResolvedSession resolved = service.resolve(sessionId).orElseThrow();

        assertEquals(USER_ID, resolved.session().userId());
        assertEquals("test-agent", resolved.session().userAgent());
        assertEquals(sessionId, resolved.sessionId());
    }

    @Test
    void expiredSessionDoesNotResolve() {

        String sessionId = service.create(USER_ID, "test-agent");

        redis.advance(properties.getSession().getTtl().plusSeconds(1));

        assertTrue(service.resolve(sessionId).isEmpty());
    }

    @Test
    void revokedSessionDoesNotResolve() {

        String sessionId = service.create(USER_ID, "test-agent");

        service.revoke(sessionId);

        assertTrue(service.resolve(sessionId).isEmpty());
    }

    @Test
    void revokingSomethingThatIsNotThereIsNotAnError() {

        service.revoke("a-session-that-never-existed");
        service.revoke(null);
        service.revoke("");
    }

    @Test
    void refreshRotatesTheIdentifierAndRetiresTheOldOne() {

        String original = service.create(USER_ID, "test-agent");

        AuthSessionService.ResolvedSession refreshed =
                service.refresh(original, "test-agent").orElseThrow();

        assertNotEquals(original, refreshed.sessionId());

        // The old key is gone outright, not merely superseded. It still resolves
        // for a moment through the grace pointer, which the next test covers.
        assertFalse(redis.exists(AuthKeyFactory.sessionKey(original)));
    }

    @Test
    void refreshExtendsTheExpiryButKeepsTheSignInTime() {

        String sessionId = service.create(USER_ID, "test-agent");

        var before = service.resolve(sessionId).orElseThrow().session();

        // Most of the session's life goes by.
        redis.advance(Duration.ofDays(29));

        AuthSessionService.ResolvedSession refreshed =
                service.refresh(sessionId, "test-agent").orElseThrow();

        var after = refreshed.session();

        // Rotating is a continuation of the same sign-in, so "signed in since"
        // must not keep sliding forward.
        assertEquals(before.createdAt(), after.createdAt());

        // And the idle time is given back: the fresh session carries the full
        // configured lifetime again rather than the remnant of the old one.
        assertTtlIs(
                properties.getSession().getTtl(),
                AuthKeyFactory.sessionKey(refreshed.sessionId()));
    }

    @Test
    void aSimultaneousSecondRefreshUsingTheOldIdentifierStillWorks() {

        String original = service.create(USER_ID, "tab-a");

        // Tab A refreshes first and rotates.
        AuthSessionService.ResolvedSession first =
                service.refresh(original, "tab-a").orElseThrow();

        // Tab B fires before it has seen the new cookie, presenting the old one.
        AuthSessionService.ResolvedSession second =
                service.refresh(original, "tab-b").orElseThrow();

        // Both end up addressing the same live session rather than one of them
        // being rejected and the tab being signed out.
        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(USER_ID, second.session().userId());
    }

    @Test
    void theGracePointerDoesNotLastForever() {

        String original = service.create(USER_ID, "tab-a");
        service.refresh(original, "tab-a");

        // Past the rotation window the old identifier is simply gone.
        redis.advance(Duration.ofMinutes(2));

        assertTrue(service.resolve(original).isEmpty());
    }

    @Test
    void refreshRefusesASessionThatDoesNotExist() {

        assertTrue(service.refresh("never-existed", "test-agent").isEmpty());
        assertTrue(service.refresh(null, "test-agent").isEmpty());
    }

    @Test
    void twoSessionsForOneUserAreIndependent() {

        String phone = service.create(USER_ID, "phone");
        String laptop = service.create(USER_ID, "laptop");

        service.revoke(phone);

        // Signing out on one device must not sign the other out.
        assertTrue(service.resolve(phone).isEmpty());
        assertEquals(USER_ID, service.resolve(laptop).orElseThrow().session().userId());
    }

    @Test
    void sessionTtlIsConfigurable() {

        properties.getSession().setTtl(Duration.ofDays(7));

        String sessionId = new AuthSessionService(redis, properties).create(USER_ID, "test-agent");

        assertTtlIs(
                Duration.ofDays(7),
                AuthKeyFactory.sessionKey(sessionId));
    }
}
