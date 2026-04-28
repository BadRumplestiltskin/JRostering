package com.magicsystems.jrostering.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the JRostering application.
 *
 * <h3>Authentication strategy</h3>
 * <ul>
 *   <li><b>Vaadin UI</b> — form-based login with HTTP session. Spring Security
 *       redirects unauthenticated requests to the login page. Vaadin handles its
 *       own CSRF protection internally; CSRF is disabled for the Vaadin servlet path.</li>
 *   <li><b>REST API ({@code /api/**})</b> — HTTP Basic authentication; stateless.
 *       CSRF is disabled for all {@code /api/**} paths.</li>
 * </ul>
 *
 * <h3>Authorisation</h3>
 * <p>All REST controllers ({@code /api/**}) require the {@code MANAGER} role, enforced via
 * {@code @PreAuthorize("hasRole('MANAGER')")} on each controller class. All authenticated
 * users are granted {@code ROLE_MANAGER} by {@link AppUserDetailsService} — single-role
 * app, but the explicit annotation means future viewer/read-only roles cannot accidentally
 * access write endpoints.</p>
 *
 * <h3>Password encoding</h3>
 * <p>BCrypt with the default strength (10 rounds). The {@link PasswordEncoder} bean
 * is used by the service layer when creating or updating user passwords and by
 * Spring Security's {@code DaoAuthenticationProvider} for credential verification.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain.
     *
     * <p>Vaadin requires that its internal paths ({@code /VAADIN/**}, {@code /vaadinServlet/**},
     * and static resources) are accessible without authentication. All other paths
     * require the user to be authenticated.</p>
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Vaadin internal resources must be publicly accessible
                .requestMatchers(
                    "/VAADIN/**",
                    "/vaadinServlet/**",
                    "/frontend/**",
                    "/icons/**",
                    "/images/**",
                    "/styles/**",
                    "/sw.js",
                    "/offline.html"
                ).permitAll()
                // Actuator liveness/readiness probes must be reachable by Kubernetes
                // without credentials; other actuator endpoints require authentication.
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()
                .requestMatchers("/actuator/**").authenticated()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf
                // CSRF is handled by Vaadin internally; disable for the API layer
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }

    /**
     * BCrypt password encoder used for hashing and verifying user passwords.
     *
     * @return a {@link BCryptPasswordEncoder} with the default strength of 10 rounds
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS policy for the REST API ({@code /api/**}).
     *
     * <p>Permits localhost origins to support local development tools and single-PC
     * deployments where the client and server share the same host. In production
     * set {@code CORS_ALLOWED_ORIGINS} to the actual origin(s) of authorised clients.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://localhost:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
