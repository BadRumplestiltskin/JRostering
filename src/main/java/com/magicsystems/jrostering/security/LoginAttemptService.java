package com.magicsystems.jrostering.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for the login form and HTTP Basic endpoint.
 *
 * <p>Tracks failed authentication attempts per username. After
 * {@link #MAX_ATTEMPTS} consecutive failures the account is locked for
 * {@link #LOCKOUT_MINUTES} minutes. A successful login resets the counter.</p>
 *
 * <p>State is in-memory, so a server restart clears all lockouts. This is
 * acceptable for a single-PC, single-manager deployment where an application
 * restart is a known administrative action. A persistent lockout counter
 * in the {@code APP_USER} table can be added if this becomes a requirement.</p>
 *
 * <p>This service is checked by {@link AppUserDetailsService} before returning
 * {@code UserDetails}. Failed and successful authentication events are recorded
 * by {@link SecurityEventListener}.</p>
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS      = 5;
    static final int LOCKOUT_MINUTES   = 15;

    private record AttemptRecord(int count, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if the given username is currently locked out.
     * An expired lockout is cleared automatically on this call.
     */
    public boolean isLocked(String username) {
        AttemptRecord record = attempts.get(username);
        if (record == null) {
            return false;
        }
        if (record.lockedUntil() != null && Instant.now().isBefore(record.lockedUntil())) {
            return true;
        }
        // Lockout has expired — clear it so the next failure starts from zero.
        attempts.remove(username);
        return false;
    }

    /**
     * Records a failed authentication attempt for the given username.
     * Locks the account after {@link #MAX_ATTEMPTS} consecutive failures.
     */
    public void loginFailed(String username) {
        attempts.merge(username, new AttemptRecord(1, null), (existing, ignored) -> {
            int newCount = existing.count() + 1;
            Instant lockUntil = newCount >= MAX_ATTEMPTS
                    ? Instant.now().plusSeconds((long) LOCKOUT_MINUTES * 60)
                    : null;
            return new AttemptRecord(newCount, lockUntil);
        });
    }

    /**
     * Clears the failed-attempt counter for the given username after a successful login.
     */
    public void loginSucceeded(String username) {
        attempts.remove(username);
    }
}
