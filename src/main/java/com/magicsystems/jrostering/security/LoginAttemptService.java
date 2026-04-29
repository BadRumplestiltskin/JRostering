package com.magicsystems.jrostering.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * In-memory brute-force protection for the login form and HTTP Basic endpoint.
 *
 * <p>Tracks failed authentication attempts per username. After
 * {@link #MAX_ATTEMPTS} consecutive failures the account is locked for
 * {@link #LOCKOUT_MINUTES} minutes. A successful login resets the counter.</p>
 *
 * <p>Attempt records are stored in a Caffeine cache (max 10 000 entries, evicted
 * 1 hour after last access). This bounds memory under long-running deployments —
 * usernames with no recent activity are automatically removed. The manual
 * {@code lockedUntil} check in {@link #isLocked} is retained for precision: a
 * lockout expires at the exact instant set at the 5th failure, regardless of
 * when the cache would next evict the entry.</p>
 *
 * <p>State is in-memory, so a server restart clears all lockouts. This is
 * acceptable for a single-PC, single-manager deployment where a restart is a
 * known administrative action.</p>
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS    = 5;
    static final int LOCKOUT_MINUTES = 15;

    private record AttemptRecord(int count, Instant lockedUntil) {}

    private final Cache<String, AttemptRecord> attempts = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    /**
     * Returns {@code true} if the given username is currently locked out.
     * An expired lockout is invalidated automatically on this call.
     */
    public boolean isLocked(String username) {
        AttemptRecord record = attempts.getIfPresent(username);
        if (record == null) {
            return false;
        }
        if (record.lockedUntil() != null && Instant.now().isBefore(record.lockedUntil())) {
            return true;
        }
        // Lockout has expired — remove so the next failure starts from zero.
        attempts.invalidate(username);
        return false;
    }

    /**
     * Records a failed authentication attempt for the given username.
     * Locks the account after {@link #MAX_ATTEMPTS} consecutive failures.
     */
    public void loginFailed(String username) {
        attempts.asMap().merge(username, new AttemptRecord(1, null), (existing, ignored) -> {
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
        attempts.invalidate(username);
    }
}
