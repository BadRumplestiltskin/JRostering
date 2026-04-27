package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.AppUser;
import com.magicsystems.jrostering.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock AppUserRepository appUserRepository;
    @Mock PasswordEncoder   passwordEncoder;

    @InjectMocks AppUserService service;

    @Test
    void changePassword_updatesHash_whenCurrentPasswordMatches() {
        AppUser user = user(1L, "admin", "$hash");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "$hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("$newhash");
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword("admin", "oldpass", "newpass");

        assertThat(user.getPasswordHash()).isEqualTo("$newhash");
        verify(appUserRepository).save(user);
    }

    @Test
    void changePassword_throwsInvalidOperation_whenCurrentPasswordWrong() {
        AppUser user = user(1L, "admin", "$hash");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("admin", "wrong", "newpass"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Current password");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void changePassword_throwsEntityNotFound_whenUserMissing() {
        when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("ghost", "x", "y"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_savesNewActiveUser() {
        when(appUserRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("$encoded");
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = service.create("  newuser  ", "pass");

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getPasswordHash()).isEqualTo("$encoded");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void create_throwsInvalidOperation_whenUsernameTaken() {
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "$h")));

        assertThatThrownBy(() -> service.create("admin", "pass"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void deactivate_setsActiveFalse() {
        AppUser user = user(1L, "admin", "$h");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(1L);

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void activate_setsActiveTrue() {
        AppUser user = user(1L, "admin", "$h");
        user.setActive(false);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activate(1L);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void deactivate_throwsEntityNotFound_whenMissing() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // -------------------------------------------------------------------------

    private static AppUser user(Long id, String username, String hash) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash(hash);
        u.setActive(true);
        return u;
    }
}
