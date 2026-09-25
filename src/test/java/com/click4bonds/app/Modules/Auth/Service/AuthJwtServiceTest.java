package com.click4bonds.app.Modules.Auth.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.click4bonds.app.Modules.Auth.AuthTestSupport;
import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * The access tokens this application issues and verifies itself.
 *
 * <p>The contract these tests pin down is the one the rest of the application
 * depends on: the subject is the user's identifier, and the role travels as a
 * claim. Everything else about the token is an implementation detail.</p>
 */
class AuthJwtServiceTest {

    private static final String USER_ID = AuthTestSupport.USER_ID;
    private static final String OTHER_SECRET = "a-completely-different-signing-secret-also-32-bytes";

    private AuthProperties properties;
    private AuthJwtService service;

    @BeforeEach
    void setUp() {
        properties = AuthTestSupport.properties();
        service = new AuthJwtService(properties);
    }

    private User customer() {
        return AuthTestSupport.user(USER_ID, AuthTestSupport.PHONE, UserRole.CUSTOMER, UserStatus.ACTIVE);
    }

    @Test
    void subjectIsTheUserIdSoExistingControllersKeepWorking() {

        String token = service.createAccessToken(customer());

        Jwt decoded = service.decode(token);

        // This is the whole compatibility requirement: controllers call
        // jwt.getSubject() and pass the result to services that expect a user id.
        assertEquals(USER_ID, decoded.getSubject());
        assertEquals(UUID.fromString(USER_ID), service.subjectAsUserId(decoded));
    }

    @Test
    void roleTravelsAsAClaim() {

        User admin = AuthTestSupport.user(USER_ID, AuthTestSupport.PHONE, UserRole.ADMIN, UserStatus.ACTIVE);

        Jwt decoded = service.decode(service.createAccessToken(admin));

        assertEquals("ADMIN", decoded.getClaimAsString(AuthJwtService.ROLE_CLAIM));
    }

    @Test
    void tokenCarriesNothingBeyondIdentityAndRole() {

        Jwt decoded = service.decode(service.createAccessToken(customer()));

        // A token is readable by anyone holding it, so nothing personal belongs
        // in one. Only the identifier and the role are present.
        assertEquals(
                java.util.Set.of("sub", "role", "iat", "exp"),
                decoded.getClaims().keySet());
    }

    @Test
    void tokenExpiresAfterTheConfiguredLifetime() {

        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));

        String token = new AuthJwtService(properties).createAccessToken(customer());

        Jwt decoded = service.decode(token);

        long lifetime = Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).toMinutes();

        assertEquals(15, lifetime);
    }

    @Test
    void expiredTokenIsRejected() {

        AuthTestSupport.MutableClock clock = new AuthTestSupport.MutableClock();
        AuthProperties shortLived = AuthTestSupport.properties();
        shortLived.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));

        AuthJwtService issuer = new AuthJwtService(shortLived, clock);
        String token = issuer.createAccessToken(customer());

        // Valid at issue time.
        assertEquals(USER_ID, issuer.decode(token).getSubject());

        // Past the lifetime, and past the tolerance the validator allows.
        clock.advance(Duration.ofMinutes(15).plusSeconds(31));

        assertThrows(JwtException.class, () -> issuer.decode(token));
    }

    @Test
    void aTokenIsStillAcceptedInsideTheClockSkewWindow() {

        AuthTestSupport.MutableClock clock = new AuthTestSupport.MutableClock();
        AuthProperties shortLived = AuthTestSupport.properties();
        shortLived.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));

        AuthJwtService issuer = new AuthJwtService(shortLived, clock);
        String token = issuer.createAccessToken(customer());

        // Just expired, but inside the tolerance that absorbs clock differences
        // between machines.
        clock.advance(Duration.ofMinutes(15).plusSeconds(5));

        assertEquals(USER_ID, issuer.decode(token).getSubject());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {

        AuthProperties other = AuthTestSupport.properties();
        other.getJwt().setSecret(OTHER_SECRET);

        String foreignToken = new AuthJwtService(other).createAccessToken(customer());

        assertThrows(JwtException.class, () -> service.decode(foreignToken));
    }

    @Test
    void aTokenWithATamperedSignatureIsRejected() {

        String token = service.createAccessToken(customer());

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        // Replace the signature wholesale rather than editing a character in
        // place: base64 leaves spare bits in the final character of a 32-byte
        // digest, so some single-character edits decode to identical bytes and
        // would not actually be a tamper.
        String tampered = parts[0] + "." + parts[1] + "."
                + (parts[2].equals("A".repeat(parts[2].length()))
                        ? "B".repeat(parts[2].length())
                        : "A".repeat(parts[2].length()));

        assertNotEquals(token, tampered);
        assertThrows(JwtException.class, () -> service.decode(tampered));
    }

    @Test
    void aTokenWithATamperedPayloadIsRejected() {

        String token = service.createAccessToken(customer());
        String[] parts = token.split("\\.");

        // Re-encode the payload with the role changed. The signature no longer
        // covers it, so this is exactly the forgery the signature exists to stop.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(
                        java.util.Base64.getUrlDecoder().decode(parts[1]),
                        java.nio.charset.StandardCharsets.UTF_8)
                        .replace("CUSTOMER", "ADMIN")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertNotEquals(token, forged);
        assertThrows(JwtException.class, () -> service.decode(forged));
    }

    @Test
    void subjectThatIsNotAUserIdIsRefused() {

        // A well-formed token whose subject is not an identifier must not reach
        // a controller, which would read it as one.
        assertThrows(JwtException.class, () -> service.subjectAsUserId(
                Jwt.withTokenValue("irrelevant")
                        .header("alg", "HS256")
                        .subject("not-a-uuid")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build()));
    }

    @Test
    void serviceRefusesToStartWithoutASecret() {

        AuthProperties missing = AuthTestSupport.properties();
        missing.getJwt().setSecret(null);

        assertThrows(IllegalStateException.class, () -> new AuthJwtService(missing));
    }

    @Test
    void serviceRefusesAWeakSecret() {

        AuthProperties weak = AuthTestSupport.properties();
        weak.getJwt().setSecret("too-short");

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> new AuthJwtService(weak));

        assertTrue(failure.getMessage().contains("at least"));
    }
}
