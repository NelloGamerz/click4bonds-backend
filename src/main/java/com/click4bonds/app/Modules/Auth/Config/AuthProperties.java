package com.click4bonds.app.Modules.Auth.Config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration of the authentication module.
 *
 * <pre>
 * auth:
 *   jwt:
 *     secret: ${AUTH_JWT_SECRET}
 *     access-token-ttl: 15m
 *   session:
 *     ttl: 30d
 *     cookie-name: ${AUTH_COOKIE_NAME:session}
 *     cookie-secure: true
 *     cookie-same-site: Lax
 *     cookie-domain:
 *   otp:
 *     max-requests-per-window: 10
 *     rate-limit-window: 15m
 * </pre>
 *
 * <p>Every value here is a policy decision that has to be changeable without a
 * rebuild: token lifetimes, how long a device stays signed in, and how hard the
 * unauthenticated OTP endpoint may be hammered. Nothing is hard-coded in the
 * services that consume this.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Session session = new Session();
    private final Otp otp = new Otp();

    /** Access-token issuance and verification. */
    @Data
    public static class Jwt {

        /**
         * Base64-encoded HMAC-SHA256 key used to sign and verify our own access
         * tokens. Supplied through the environment and never committed.
         *
         * <p>HMAC rather than RSA because this is a single-service deployment:
         * there is no second party that needs to verify a signature without
         * holding the signing capability, and no key distribution problem is
         * solved by an asymmetric key here. Were a separate verifier to appear,
         * this is the seam to change.</p>
         */
        private String secret;

        /**
         * Lifetime of an access token. Deliberately short: the refresh session
         * is what carries a long-lived login, so a leaked access token is only
         * useful for this window.
         */
        private Duration accessTokenTtl = Duration.ofMinutes(15);
    }

    /** Server-side Redis sessions behind the refresh cookie. */
    @Data
    public static class Session {

        /**
         * How long a device stays signed in. This is the lifetime of the Redis
         * session, and therefore of the refresh cookie.
         */
        private Duration ttl = Duration.ofDays(30);

        /**
         * Name of the cookie carrying the session identifier.
         *
         * <p>Defaults to a {@code __Host-} prefixed name, which browsers only
         * accept when the cookie is Secure, has {@code Path=/} and carries no
         * Domain — a combination that stops a subdomain from overwriting the
         * session cookie. Local development over plain HTTP cannot satisfy that,
         * so the dev profile overrides this with an unprefixed name.</p>
         */
        private String cookieName = "__Host-session";

        /** Whether the cookie carries the Secure flag. Must be true in production. */
        private boolean cookieSecure = true;

        /**
         * SameSite policy. {@code Lax} is the default because the frontend and
         * the API share a registrable domain, so same-site requests still carry
         * the cookie while cross-site ones do not — which is what defends the
         * cookie-bearing refresh and logout endpoints against CSRF. A
         * {@code None} policy removes that defence and is refused unless the
         * cookie is also Secure.
         */
        private String cookieSameSite = "Lax";

        /**
         * Domain to scope the cookie to. Left empty on purpose: the default
         * host-only scope is narrower than any explicit domain, and a domain
         * attribute is incompatible with the {@code __Host-} prefix.
         */
        private String cookieDomain;
    }

    /** Abuse limits on the unauthenticated OTP endpoints. */
    @Data
    public static class Otp {

        /**
         * How many OTP requests a single client address may make inside one
         * window.
         *
         * <p>The OTP module's own resend cooldown is keyed by phone number, so
         * it stops a repeat for one number but not a sweep across many. This is
         * the limit that does — and it costs money per SMS, so it is worth
         * having.</p>
         */
        private int maxRequestsPerWindow = 10;

        /** Length of the per-address window described above. */
        private Duration rateLimitWindow = Duration.ofMinutes(15);
    }
}
