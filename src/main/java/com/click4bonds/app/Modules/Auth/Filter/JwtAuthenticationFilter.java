package com.click4bonds.app.Modules.Auth.Filter;

import java.io.IOException;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates a request from its bearer token.
 *
 * <p>Reads {@code Authorization: Bearer <token>}, verifies the token's
 * signature and expiry, and — if it is good — puts an authentication into the
 * security context whose principal is the {@link Jwt} itself.</p>
 *
 * <p>Keeping the principal a {@code Jwt} is what lets every existing
 * controller keep working unchanged: {@code @AuthenticationPrincipal Jwt jwt}
 * still resolves, and {@code jwt.getSubject()} still returns the user
 * identifier, because that is what the subject is. The claim layout is the
 * contract this filter upholds.</p>
 *
 * <p>A token that fails verification is not an error the filter reports. It
 * clears the context and carries on, leaving the request unauthenticated; the
 * authorization rules then decide whether that matters. This is what lets a
 * public endpoint stay public when a caller happens to send a stale token, and
 * it keeps token failures from being distinguishable from simply not being
 * signed in.</p>
 *
 * <p>Nothing here logs the token, or any part of it.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";

    /**
     * Scheme prefix, matched case-insensitively: RFC 6750 defines the scheme
     * name as case-insensitive, and clients do send {@code bearer}.
     */
    private static final String BEARER_PREFIX = "bearer ";

    private final AuthJwtService authJwtService;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Verifies the token and installs the resulting authentication.
     *
     * <p>Every failure mode — bad signature, expired, malformed, wrong issuer
     * of subject — ends the same way: no authentication, and no detail handed
     * back to the caller.</p>
     */
    private void authenticate(HttpServletRequest request, String token) {

        try {
            Jwt jwt = authJwtService.decode(token);

            // The subject has to be a user identifier before anything downstream
            // relies on it. Controllers read it as one, so a token whose subject
            // is not a UUID is rejected here rather than allowed to fail later
            // as a 500.
            authJwtService.subjectAsUserId(jwt);

            AbstractAuthenticationToken authentication =
                    jwtAuthenticationConverter.convert(jwt);

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ex) {
            // Only the failure's type is logged, never its message: a decoding
            // error from the JWT library routinely quotes the token it could
            // not read, which would put a credential in the log. The reason is
            // not returned to the caller either, so a rejected token is
            // indistinguishable from no token.
            log.debug("Bearer token rejected ({})", ex.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * @return the raw token, or {@code null} when the request carries none
     */
    private String extractToken(HttpServletRequest request) {

        String header = request.getHeader(HEADER);

        if (header == null || header.length() <= BEARER_PREFIX.length()) {
            return null;
        }

        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }
}
