package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link AppUser} entities.
 * Used by {@code AppUserDetailsService} to authenticate roster managers.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Finds a user by username (case-sensitive). */
    Optional<AppUser> findByUsername(String username);
}
