package com.click4bonds.app.Modules.Auth.Filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.click4bonds.app.Modules.Auth.AuthTestSupport;
import com.click4bonds.app.Modules.Auth.Config.AuthProperties;
import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;

/**
 * The filter that turns a bearer token into an authentication.
 *
 * <p>The contract under test is the one the whole application already depends
 * on: after this filter runs, the principal in the security context is a
 * {@link Jwt} whose subject is the user identifier and whose role claim has
 * become a {@code ROLE_} authority. That is precisely what
 * {@code @AuthenticationPrincipal Jwt jwt} and {@code jwt.getSubject()} in the
 * existing controllers receive, so a change here would break them.</p>
 */
class JwtAuthenticationFilterTest {

    private static final String USER_ID = AuthTestSupport.USER_ID;

    private AuthProperties properties;
    private AuthJwtService jwts;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {

        properties = AuthTestSupport.properties();
        jwts = new AuthJwtService(properties);
        filter = new JwtAuthenticationFilter(jwts, AuthTestSupport.authenticationConverter());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String tokenFor(UserRole role) {

        return jwts.createAccessToken(
                AuthTestSupport.user(USER_ID, AuthTestSupport.PHONE, role, UserStatus.ACTIVE));
    }

    private void run(String authorizationHeader) throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/holdings/my");

        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private Jwt principal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        assertNotNull(principal, "expected an authenticated principal");

        return (Jwt) principal;
    }

    private Set<String> authorities() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------
    // The compatibility contract
    // ------------------------------------------------------------------

    @Test
    void aValidTokenAuthenticatesWithAJwtPrincipal() throws Exception {

        run("Bearer " + tokenFor(UserRole.CUSTOMER));

        // @AuthenticationPrincipal Jwt jwt resolves to this object.
        Jwt jwt = principal();

        // jwt.getSubject() is the user identifier controllers pass on.
        assertEquals(USER_ID, jwt.getSubject());
    }

    @Test
    void customerRoleBecomesRoleCustomer() throws Exception {

        run("Bearer " + tokenFor(UserRole.CUSTOMER));

        assertTrue(authorities().contains("ROLE_CUSTOMER"));
        assertFalse(authorities().contains("ROLE_ADMIN"));
    }

    @Test
    void adminRoleBecomesRoleAdmin() throws Exception {

        run("Bearer " + tokenFor(UserRole.ADMIN));

        assertTrue(authorities().contains("ROLE_ADMIN"));
        assertFalse(authorities().contains("ROLE_CUSTOMER"));
    }

    @Test
    void everyRoleMapsToItsPrefixedAuthority() throws Exception {

        for (UserRole role : UserRole.values()) {

            SecurityContextHolder.clearContext();
            run("Bearer " + tokenFor(role));

            assertTrue(
                    authorities().contains("ROLE_" + role.name()),
                    "expected ROLE_" + role.name() + " in " + authorities());
        }
    }

    @Test
    void theAuthoritySetMatchesWhatTheExistingPreAuthorizeExpressionsExpect() throws Exception {

        run("Bearer " + tokenFor(UserRole.ADMIN));

        // The converter also attaches the bearer factor, which Spring Security
        // uses for step-up decisions. hasRole('ADMIN') matches on the ROLE_
        // authority alone, so the extra entry does not affect any existing
        // @PreAuthorize expression — asserted here so a change to that set is
        // noticed rather than assumed.
        assertEquals(Set.of("ROLE_ADMIN", "FACTOR_BEARER"), authorities());
    }

    @Test
    void theBearerSchemeIsCaseInsensitive() throws Exception {

        // RFC 6750 defines the scheme as case-insensitive, and clients do vary.
        run("bearer " + tokenFor(UserRole.CUSTOMER));

        assertEquals(USER_ID, principal().getSubject());
    }

    // ------------------------------------------------------------------
    // Rejection
    // ------------------------------------------------------------------

    @Test
    void noTokenLeavesTheRequestUnauthenticated() throws Exception {

        run(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void aTokenSignedWithAnotherKeyLeavesTheRequestUnauthenticated() throws Exception {

        AuthProperties other = AuthTestSupport.properties();
        other.getJwt().setSecret("a-completely-different-signing-secret-also-32-bytes");

        String foreign = new AuthJwtService(other).createAccessToken(
                AuthTestSupport.user(USER_ID, AuthTestSupport.PHONE, UserRole.ADMIN, UserStatus.ACTIVE));

        run("Bearer " + foreign);

        // Notably not an error: the request is simply not authenticated, so a
        // public endpoint stays reachable and a protected one is refused.
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void anExpiredTokenLeavesTheRequestUnauthenticated() throws Exception {

        // Minted an hour ago with a fifteen-minute life, so it is genuinely
        // expired relative to the real clock this filter validates against.
        String expired = new AuthJwtService(properties, AuthTestSupport.MutableClock.inThePast())
                .createAccessToken(
                        AuthTestSupport.user(
                                USER_ID, AuthTestSupport.PHONE, UserRole.CUSTOMER, UserStatus.ACTIVE));

        run("Bearer " + expired);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void aMalformedTokenLeavesTheRequestUnauthenticated() throws Exception {

        run("Bearer not-a-jwt-at-all");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void aTokenWhoseSubjectIsNotAUserIdIsRefused() throws Exception {

        // A correctly signed token whose subject follows some other convention
        // must not reach a controller, which would read it as a user id and
        // fail later. The filter rejects it up front instead.
        Jwt notAnIdentifier = Jwt.withTokenValue("validly-signed-but-wrong-subject")
                .header("alg", "HS256")
                .subject("usr_2NrK8pQmZ1")
                .claim(AuthJwtService.ROLE_CLAIM, "CUSTOMER")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(300))
                .build();

        JwtAuthenticationFilter withForeignSubject = new JwtAuthenticationFilter(
                new AuthJwtService(properties) {
                    @Override
                    public Jwt decode(String token) {
                        return notAnIdentifier;
                    }
                },
                AuthTestSupport.authenticationConverter());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer whatever");

        withForeignSubject.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void anEmptyBearerValueIsIgnored() throws Exception {

        run("Bearer ");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void aNonBearerSchemeIsIgnored() throws Exception {

        run("Basic dXNlcjpwYXNz");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void anAuthenticationAlreadyInTheContextIsNotOverwritten() throws Exception {

        var existing = new org.springframework.security.authentication.TestingAuthenticationToken(
                "someone-else", null, "ROLE_ADMIN");

        SecurityContextHolder.getContext().setAuthentication(existing);

        run("Bearer " + tokenFor(UserRole.CUSTOMER));

        assertEquals(existing, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void theRequestIsAlwaysPassedDownTheChain() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();

        // A rejected token must not fail the request on its own.
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        assertTrue(true);
    }
}
