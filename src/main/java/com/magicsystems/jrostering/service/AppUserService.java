package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.AppUser;
import com.magicsystems.jrostering.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for managing {@link AppUser} accounts.
 *
 * <p>Password hashing is delegated to the injected {@link PasswordEncoder} (BCrypt).
 * Plain-text passwords are never stored.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder   passwordEncoder;

    @Transactional(readOnly = true)
    public List<AppUser> getAll() {
        return appUserRepository.findAll();
    }

    /**
     * Changes the password for the given user after verifying the current password.
     *
     * @throws InvalidOperationException if {@code currentPassword} does not match
     * @throws EntityNotFoundException   if the username does not exist
     */
    public void changePassword(String username, String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidOperationException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
    }

    /**
     * Creates a new active user account.
     *
     * @throws InvalidOperationException if the username is already taken
     */
    public AppUser create(String username, String password) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            throw new InvalidOperationException("Username '" + username + "' is already taken");
        }
        AppUser user = new AppUser();
        user.setUsername(username.strip());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        return appUserRepository.save(user);
    }

    /** Soft-deactivates a user so they can no longer log in. */
    public void deactivate(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("AppUser", id));
        user.setActive(false);
        appUserRepository.save(user);
    }

    /** Re-activates a previously deactivated user. */
    public void activate(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("AppUser", id));
        user.setActive(true);
        appUserRepository.save(user);
    }
}
