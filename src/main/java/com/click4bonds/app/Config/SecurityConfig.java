package com.click4bonds.app.Config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .authorizeHttpRequests(auth -> auth

                                                // Public endpoints
                                                .requestMatchers(
                                                                "/public/**",
                                                                "/api/webhooks/clerk",
                                                                "/actuator/health",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**",
                                                                "/error")
                                                .permitAll()

                                                // Public bond endpoints
                                                // .requestMatchers(
                                                // HttpMethod.GET,
                                                // "/api/bonds/**")
                                                // .permitAll()

                                                .requestMatchers(HttpMethod.GET, "/api/bonds", "/api/bonds/**")
                                                .permitAll()

                                                // Everything else requires authentication
                                                .anyRequest()
                                                .authenticated())

                                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

                return http.build();
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        String role = jwt.getClaimAsString("role");

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