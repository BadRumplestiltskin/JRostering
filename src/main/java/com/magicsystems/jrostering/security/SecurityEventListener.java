package com.magicsystems.jrostering.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Listens for Spring Security authentication events and forwards them to
 * {@link LoginAttemptService} to maintain the per-username failure counter.
 *
 * <p>Spring Boot auto-configures {@code DefaultAuthenticationEventPublisher}, so
 * standard success and failure events are published automatically for both
 * form-login and HTTP Basic authentication without additional configuration.</p>
 *
 * <p>{@link AbstractAuthenticationFailureEvent} covers all failure subtypes
 * (bad credentials, disabled account, locked account, expired credentials, etc.).
 * Using the abstract base class means that a lockout-triggered failure also
 * extends the lockout — intentional, since the lockout itself is the signal
 * of an ongoing attack.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityEventListener {

    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.loginFailed(username);
        log.debug("Failed login attempt recorded for username='{}'", username);
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.loginSucceeded(username);
    }
}
