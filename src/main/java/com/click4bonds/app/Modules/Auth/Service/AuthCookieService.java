package com.click4bonds.app.Modules.Auth.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Auth.Config.AuthProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the cookie that carries the session identifier.
 *
 * <p>The cookie is {@code HttpOnly}, so no script can read the session
 * identifier — an XSS foothold cannot be turned into a stolen login. It is
 * never returned in a response body and never placed in web storage; the
 * browser attaches it to the refresh and logout endpoints on its own, which is
 * the only thing it is for.</p>
 *
 * <p>Because the cookie is attached automatically, those two endpoints are
 * reachable by a cross-site form post in a way a {@code Authorization} header
 * is not. The defence is {@code SameSite}: with {@code Lax} the browser
 * withholds the cookie from cross-site requests entirely, so a forged request
 * arrives with no session and is rejected. A {@code None} policy would remove
 * that defence, which is why it is refused unless the cookie is also
 * {@code Secure}.</p>
 *
 * <p>Misconfiguration is refused at startup rather than noticed later: a
 * {@code __Host-} prefixed name that does not satisfy the prefix's rules would
 * be dropped by the browser silently, and a login that appears to succeed while
 * the cookie never arrives is far harder to diagnose in production than a
 * failed boot.</p>
 */
@Slf4j
@Component
public class AuthCookieService {

    /** Prefix browsers only accept on Secure, Path=/, domain-less cookies. */
    private static final String HOST_PREFIX = "__Host-";

    private static final String PATH = "/";

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {

        this.properties = properties;

        validate();

        AuthProperties.Session session = properties.getSession();

        log.info(
                "Session cookie '{}' (secure={}, sameSite={}, maxAge={})",
                session.getCookieName(),
                session.isCookieSecure(),
                session.getCookieSameSite(),
                session.getTtl());
    }

    /**
     * Builds the {@code Set-Cookie} header that installs a session.
     *
     * @param sessionId identifier to store
     * @return the header to add to the response
     */
    public String issueCookie(String sessionId) {

        return build(sessionId, properties.getSession().getTtl()).toString();
    }

    /**
     * Builds the {@code Set-Cookie} header that removes the cookie.
     *
     * <p>Every attribute has to match the one that set it — name, path, and
     * whether it was Secure or host-prefixed. A deletion that differs in any of
     * them targets a different cookie and leaves the original in place.</p>
     *
     * @return the header to add to the response
     */
    public String clearCookie() {
        return build("", Duration.ZERO).toString();
    }

    /**
     * @param request incoming request
     * @return the session identifier the browser sent, if any
     */
    public Optional<String> readSessionId(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        String name = properties.getSession().getCookieName();

        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String value, Duration maxAge) {

        AuthProperties.Session session = properties.getSession();

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(session.getCookieName(), value)
                .httpOnly(true)
                .secure(session.isCookieSecure())
                .path(PATH)
                .sameSite(session.getCookieSameSite())
                .maxAge(maxAge);

        // Set only when configured. A Domain attribute widens the cookie's
        // reach to every subdomain, so the narrower host-only default is the
        // right one unless a deployment genuinely needs otherwise.
        if (session.getCookieDomain() != null && !session.getCookieDomain().isBlank()) {
            builder.domain(session.getCookieDomain());
        }

        return builder.build();
    }

    private void validate() {

        AuthProperties.Session session = properties.getSession();
        String sameSite = session.getCookieSameSite();
        String name = session.getCookieName();

        if (name == null || name.isBlank()) {
            throw new IllegalStateException("auth.session.cookie-name must not be blank");
        }

        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalStateException(
                    "auth.session.cookie-same-site must be configured (Lax, Strict or None)");
        }

        // None opts out of the browser's cross-site protection, which is the
        // only thing standing in front of the cookie-bearing endpoints. It is
        // acceptable over TLS and not otherwise.
        if ("None".equalsIgnoreCase(sameSite) && !session.isCookieSecure()) {
            throw new IllegalStateException(
                    "auth.session.cookie-same-site=None requires auth.session.cookie-secure=true; "
                            + "without Secure the session cookie would be sent over plain HTTP");
        }

        if (name.startsWith(HOST_PREFIX)) {

            if (!session.isCookieSecure()) {
                throw new IllegalStateException(
                        "A '" + HOST_PREFIX + "' cookie must be Secure; set auth.session.cookie-secure=true "
                                + "or choose an unprefixed cookie name for non-HTTPS environments");
            }

            if (session.getCookieDomain() != null && !session.getCookieDomain().isBlank()) {
                throw new IllegalStateException(
                        "A '" + HOST_PREFIX + "' cookie must not carry a Domain attribute");
            }
        }
    }

    /**
     * @return the header name these values belong under
     */
    public static String headerName() {
        return HttpHeaders.SET_COOKIE;
    }
}
