package com.click4bonds.app.Config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.click4bonds.app.Modules.Auth.Filter.JwtAuthenticationFilter;
import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;

/**
 * Authorization rules and the filter chain that enforces them.
 *
 * <p>Requests are authenticated by {@link JwtAuthenticationFilter}, which reads
 * this application's own access tokens. There is no external identity provider:
 * tokens are signed and verified here, and the identity in them is a local user
 * identifier.</p>
 *
 * <h2>CSRF</h2>
 *
 * <p>CSRF protection is off, and that is a decision rather than an omission.
 * The reasoning rests on what actually carries credentials:</p>
 *
 * <ul>
 *   <li>Every state-changing API call is authenticated by an
 *       {@code Authorization} header. A browser never attaches that header on
 *       its own, so a cross-site request cannot carry it and there is nothing
 *       for an attacker to ride.</li>
 *   <li>{@code /auth/refresh} and {@code /auth/logout} are the exceptions —
 *       they authenticate by cookie, so a browser <em>would</em> attach the
 *       credential to a forged cross-site request. They are protected by
 *       {@code SameSite=Lax} on that cookie, which withholds it from any
 *       cross-site request. The frontend and the API share a registrable
 *       domain, so legitimate calls are same-site and still carry it.</li>
 *   <li>CORS is restricted to explicit origins with credentials allowed, so a
 *       cross-origin caller cannot read a response even where it could send a
 *       request.</li>
 * </ul>
 *
 * <p>What would change this: setting the session cookie to {@code SameSite=None}
 * for a genuinely cross-site deployment. That would remove the protection the
 * two cookie endpoints rely on, and a CSRF token would become necessary.
 * {@code AuthCookieService} refuses to start in the one configuration that makes
 * that mistake silently (None without Secure), but it cannot tell whether the
 * deployment is cross-site — so that remains a decision to make deliberately.</p>
 *
 * <h2>Method security</h2>
 *
 * <p>{@code @PreAuthorize} is not enabled here, so the annotations on the
 * controllers currently have no effect beyond documentation.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        AuthJwtService authJwtService,
                        JwtAuthenticationConverter jwtAuthenticationConverter)
                        throws Exception {

                http
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())

                                // No container session is created or consulted.
                                // The only session in this application is the one
                                // held in Redis and identified by the cookie.
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // Reads the profile of the account the
                                                // token names, so it needs the token.
                                                // Declared before the rule below, because
                                                // matchers are evaluated in order and the
                                                // first match wins — without this the
                                                // /auth/** permitAll would swallow it.
                                                .requestMatchers(HttpMethod.GET, "/auth/me")
                                                .authenticated()

                                                // Sign-in. Reachable without a token
                                                // because these calls are how a caller
                                                // obtains one.
                                                .requestMatchers("/auth/**")
                                                .permitAll()

                                                // Public endpoints
                                                .requestMatchers(
                                                                "/public/**",
                                                                "/actuator/health",
                                                                "/actuator/health/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**",
                                                                "/error",
                                                                "/api/v1/contact-inquiries")
                                                .permitAll()

                                                // Analytics smoke test. The controller behind
                                                // this path only exists under the dev profile, so
                                                // in any other environment the rule matches
                                                // nothing and the path 404s.
                                                // .requestMatchers(HttpMethod.POST, "/api/analytics/test")
                                                // .permitAll()

                                                // Public bond endpoints
                                                .requestMatchers(HttpMethod.GET, "/api/bonds", "/api/bonds/**")
                                                .permitAll()

                                                // Everything else requires authentication
                                                .anyRequest()
                                                .authenticated())

                                // A missing or rejected token answers 401 with a
                                // WWW-Authenticate challenge, so a client can tell
                                // "sign in again" apart from "you may not do this"
                                // and knows to refresh.
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(
                                                                new BearerTokenAuthenticationEntryPoint()))

                                .addFilterBefore(
                                                new JwtAuthenticationFilter(
                                                                authJwtService,
                                                                jwtAuthenticationConverter),
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * Maps the role claim onto Spring Security authorities.
         *
         * <p>A token carries the user's role as its {@code UserRole} name, so
         * {@code CUSTOMER} becomes {@code ROLE_CUSTOMER} — the same convention
         * {@code @PreAuthorize("hasRole('CUSTOMER')")} expects. The role comes
         * from the user's record at the moment the token was issued, so it is
         * always one of the values that enum defines.</p>
         *
         * <p>Used by {@link JwtAuthenticationFilter} when it builds the
         * authentication for a request.</p>
         */
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        String role = jwt.getClaimAsString(AuthJwtService.ROLE_CLAIM);

                        if (role == null) {
                                return List.of();
                        }

                        return List.of(
                                        new SimpleGrantedAuthority(
                                                        "ROLE_" + role.toUpperCase()));
                });

                return converter;
        }
}
