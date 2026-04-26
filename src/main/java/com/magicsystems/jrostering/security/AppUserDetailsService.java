package com.magicsystems.jrostering.security;

import com.magicsystems.jrostering.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security {@link UserDetailsService} implementation backed by the
 * {@code APP_USER} table.
 *
 * <p>Loads a user by username and builds a Spring Security {@link UserDetails}
 * instance from the stored bcrypt password hash. Inactive users are rejected
 * with a {@link UsernameNotFoundException} to prevent login.</p>
 *
 * <p>All authenticated users are granted the {@code ROLE_USER} authority.
 * Role-based access control is not required in the initial version.</p>
 */
@Service
@Transactional(readOnly = true)
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Locates the user by {@code username}. Throws {@link UsernameNotFoundException}
     * if no active user with that username exists.
     *
     * @param username the username to look up (case-sensitive)
     * @return a fully populated {@link UserDetails} instance
     * @throws UsernameNotFoundException if the user does not exist or is inactive
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByUsername(username)
                .filter(com.magicsystems.jrostering.domain.AppUser::isActive)
                .map(appUser -> User.builder()
                        .username(appUser.getUsername())
                        .password(appUser.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() ->
                        new UsernameNotFoundException("No active user found with username: " + username));
    }
}
