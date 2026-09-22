package com.click4bonds.app.Modules.Auth.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.User.Model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.extern.slf4j.Slf4j;

/**
 * Issues and validates this application's own access tokens.
 *
 * <p>These tokens are signed with a key this application owns and verified by
 * this application alone. There is no issuer, no JWKS endpoint and no external
 * identity provider in the loop: possession of a valid token means only that
 * this service minted it for a live session.</p>
 *
 * <p>The subject is the internal {@code User.id}, and the only other claim
 * carried is the role. Nothing else about the user travels in the token — not
 * the email, not the phone number, not the name — because a token is readable
 * by anyone holding it, and a role is the only thing the authorization layer
 * needs. The token is short-lived by configuration, so a role that changes
 * takes effect at the next refresh rather than living on in a stale token
 * until it expires.</p>
 */
@Slf4j
@Service
public class AuthJwtService {

    /** Role claim. Held as the {@code UserRole} name, e.g. {@code CUSTOMER}. */
    public static final String ROLE_CLAIM = "role";

    private static final String HMAC = "HmacSHA256";

    /** HS256 requires at least a 256-bit key; anything shorter is refused. */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Tolerance applied when comparing a token's timestamps against the clock.
     *
     * <p>Set explicitly rather than left to the library's default, so the window
     * in which a just-expired token is still accepted is a stated decision. It
     * absorbs the small clock differences between the machine that issued a
     * token and the one validating it, and it is short enough that an access
     * token's real lifetime stays close to its configured one.</p>
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties properties;
    private final Clock clock;

    /**
     * The constructor Spring uses.
     *
     * <p>Annotated because the clock-taking overload below is a second
     * candidate: without naming one, the container has no way to choose and
     * refuses to start.</p>
     */
    @Autowired
    public AuthJwtService(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * @param clock source of the current time, for issuance and validation;
     *              injectable so expiry can be tested without waiting for it
     */
    public AuthJwtService(AuthProperties properties, Clock clock) {

        this.properties = properties;
        this.clock = clock;

        SecretKey key = signingKey(properties.getJwt().getSecret());

        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Signature verification is the decoder's; this installs the other half
        // — refusing a token whose expiry has passed. There is no issuer to
        // check, because this application is the only issuer it trusts.
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);

        this.decoder = decoder;

        log.info(
                "Access tokens are signed with HMAC-SHA256 and expire after {} (clock skew {}s)",
                properties.getJwt().getAccessTokenTtl(),
                CLOCK_SKEW.toSeconds());
    }

    /**
     * Mints an access token for a user.
     *
     * @param user account the token is issued to
     * @return a signed, short-lived token whose subject is {@code user.id}
     */
    public String createAccessToken(User user) {

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getJwt().getAccessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return encoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    /**
     * Verifies a token's signature and expiry.
     *
     * <p>Both checks are the decoder's: it rejects anything not signed with our
     * key, and anything whose {@code exp} has passed, before this method sees
     * it. The caller therefore only ever handles tokens this service minted and
     * that are still live.</p>
     *
     * @param token raw bearer token
     * @return the verified token
     * @throws JwtException when the signature is wrong, the token is malformed
     *                      or it has expired
     */
    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    /**
     * @param token verified token
     * @return its subject as the user identifier it names
     * @throws JwtException when the subject is absent or is not a UUID
     */
    public UUID subjectAsUserId(Jwt token) {

        String subject = token.getSubject();

        if (subject == null) {
            throw new JwtException("Token has no subject");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Token subject is not a user identifier");
        }
    }

    /**
     * Reads the configured secret into a key.
     *
     * <p>The value is used as its own bytes. It is not decoded, and deliberately
     * so: guessing at an encoding would mean a secret that happens to look like
     * base64 silently becomes a different, shorter key than the operator
     * intended — or fails a length check for no visible reason. Whatever string
     * is configured is the key material, and its length is the only rule.</p>
     *
     * <p>A missing or weak secret is fatal at startup rather than at first
     * login. An application that cannot sign tokens has no business running,
     * and failing here is what stops a deployment from quietly coming up with a
     * predictable key. The minimum is the length HS256 itself requires.</p>
     */
    private SecretKey signingKey(String secret) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "auth.jwt.secret must be configured (set the AUTH_JWT_SECRET environment variable)");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "auth.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes, but was " + keyBytes.length
                            + " (generate one with: openssl rand -base64 48)");
        }

        return new SecretKeySpec(keyBytes, HMAC);
    }
}
