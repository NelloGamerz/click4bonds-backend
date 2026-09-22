package com.click4bonds.app.Modules.Auth.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.click4bonds.app.Modules.Auth.AuthTestSupport;
import com.click4bonds.app.Modules.Auth.Config.AuthProperties;

/**
 * The cookie that carries the session identifier.
 *
 * <p>These assertions are about the attributes the browser enforces, because
 * each of them is the difference between a session that is safe to rely on and
 * one that is not.</p>
 */
class AuthCookieServiceTest {

    private static AuthProperties withCookie(String name, boolean secure, String sameSite) {

        AuthProperties properties = AuthTestSupport.properties();

        properties.getSession().setCookieName(name);
        properties.getSession().setCookieSecure(secure);
        properties.getSession().setCookieSameSite(sameSite);

        return properties;
    }

    @Test
    void cookieIsHttpOnlySoScriptsCannotReadIt() {

        String header = new AuthCookieService(AuthTestSupport.properties()).issueCookie("abc");

        assertTrue(header.contains("HttpOnly"));
    }

    @Test
    void cookieIsScopedToTheWholeSiteAndExpires() {

        String header = new AuthCookieService(AuthTestSupport.properties()).issueCookie("abc");

        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("Max-Age="));
        // The session identifier appears exactly once, as the value.
        assertEquals(1, header.split("abc", -1).length - 1);
    }

    @Test
    void sameSiteIsConfigurable() {

        String lax = new AuthCookieService(withCookie("session", true, "Lax")).issueCookie("abc");
        assertTrue(lax.contains("SameSite=Lax"));

        String strict = new AuthCookieService(withCookie("session", true, "Strict")).issueCookie("abc");
        assertTrue(strict.contains("SameSite=Strict"));
    }

    @Test
    void secureFlagFollowsConfiguration() {

        assertTrue(
                new AuthCookieService(withCookie("session", true, "Lax"))
                        .issueCookie("abc").contains("Secure"));

        assertFalse(
                new AuthCookieService(withCookie("session", false, "Lax"))
                        .issueCookie("abc").contains("Secure"));
    }

    @Test
    void clearingMatchesTheAttributesThatSetIt() {

        AuthCookieService service = new AuthCookieService(AuthTestSupport.properties());

        String issued = service.issueCookie("abc");
        String cleared = service.clearCookie();

        // A deletion that differs in name, path or flags targets a different
        // cookie and leaves the real one in place.
        assertTrue(cleared.contains("session="));
        assertTrue(cleared.contains("Path=/"));
        assertTrue(cleared.contains("Max-Age=0"));
        assertEquals(
                issued.contains("HttpOnly"),
                cleared.contains("HttpOnly"));
        assertEquals(
                issued.contains("SameSite=Lax"),
                cleared.contains("SameSite=Lax"));
    }

    @Test
    void hostPrefixedNameIsRefusedWithoutSecureBecauseBrowsersWouldDropIt() {

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new AuthCookieService(withCookie("__Host-session", false, "Lax")));

        assertTrue(failure.getMessage().contains("__Host-"));
    }

    @Test
    void hostPrefixedNameIsRefusedWithADomainAttribute() {

        AuthProperties properties = withCookie("__Host-session", true, "Lax");
        properties.getSession().setCookieDomain("example.com");

        assertThrows(IllegalStateException.class, () -> new AuthCookieService(properties));
    }

    @Test
    void sameSiteNoneIsRefusedWithoutSecure() {

        // None opts out of the browser's cross-site protection, which is the
        // only thing guarding the cookie-bearing endpoints.
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new AuthCookieService(withCookie("session", false, "None")));

        assertTrue(failure.getMessage().contains("Secure"));
    }

    @Test
    void sameSiteNoneIsAllowedOverHttps() {

        String header = new AuthCookieService(withCookie("__Host-session", true, "None"))
                .issueCookie("abc");

        assertTrue(header.contains("SameSite=None"));
        assertTrue(header.contains("Secure"));
    }

    @Test
    void blankConfigurationIsRefused() {

        assertThrows(
                IllegalStateException.class,
                () -> new AuthCookieService(withCookie("", true, "Lax")));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthCookieService(withCookie("session", true, "")));
    }

    @Test
    void requestCookieIsReadBack() {

        AuthCookieService service = new AuthCookieService(AuthTestSupport.properties());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new jakarta.servlet.http.Cookie("other", "ignored"),
                new jakarta.servlet.http.Cookie("session", "the-session-id"));

        assertEquals("the-session-id", service.readSessionId(request).orElseThrow());
    }

    @Test
    void absentCookieReadsAsEmpty() {

        AuthCookieService service = new AuthCookieService(AuthTestSupport.properties());

        assertTrue(service.readSessionId(new MockHttpServletRequest()).isEmpty());

        MockHttpServletRequest withBlank = new MockHttpServletRequest();
        withBlank.setCookies(new jakarta.servlet.http.Cookie("session", ""));

        assertTrue(service.readSessionId(withBlank).isEmpty());
    }
}
