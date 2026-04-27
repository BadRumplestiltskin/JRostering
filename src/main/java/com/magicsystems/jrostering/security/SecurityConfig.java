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
 * <p>All authenticated users have full access to all features. Role-based
 * access control is not required in the initial version.</p>
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
                // Actuator health is public so load-balancers can probe it without credentials;
                // other actuator endpoints require authentication.
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").authenticated()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                // The Vaadin LoginView will be wired here once the UI package is implemented.
                // Until then, Spring Security's default generated login page is used.
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
}
