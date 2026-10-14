package com.example.flags;

import com.example.flags.UserDtos.CreateUserRequest;
import com.example.flags.UserDtos.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuthTokenRepository tokens = mock(AuthTokenRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserService service = new UserService(users, tokens, encoder);
    private final UUID id = UUID.randomUUID();

    private AppUser existing(Role role, boolean enabled) {
        AppUser u = new AppUser("alex", encoder.encode("a-long-password"), role);
        u.setEnabled(enabled);
        when(users.findById(id)).thenReturn(Optional.of(u));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        return u;
    }

    @Test
    void createsAUserWithAHashedPasswordAndTheChosenRole() {
        when(users.findByUsername("sam")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(new CreateUserRequest("Sam", "a-long-password", Role.DEVELOPER));

        assertEquals("sam", created.username());
        assertEquals(Role.DEVELOPER, created.role());
        verify(users).save(argThat(u -> encoder.matches("a-long-password", u.getPasswordHash())));
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(users.findByUsername("sam")).thenReturn(Optional.of(new AppUser("sam", "x", Role.VIEWER)));
        ApiException e = assertThrows(ApiException.class,
                () -> service.create(new CreateUserRequest("sam", "a-long-password", Role.VIEWER)));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void shortPasswordIsRejected() {
        when(users.findByUsername("sam")).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class,
                () -> service.create(new CreateUserRequest("sam", "short", Role.VIEWER)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void theLastActiveAdministratorCannotBeDemotedOrDisabled() {
        existing(Role.ADMIN, true);
        when(users.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(1L);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.update(id, new UpdateUserRequest(Role.DEVELOPER, true))).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.update(id, new UpdateUserRequest(Role.ADMIN, false))).getStatus());
    }

    @Test
    void anAdministratorCanBeDemotedWhenAnotherOneRemains() {
        AppUser u = existing(Role.ADMIN, true);
        when(users.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(2L);

        service.update(id, new UpdateUserRequest(Role.DEVELOPER, true));
        assertEquals(Role.DEVELOPER, u.getRole());
    }

    @Test
    void disablingAUserSignsThemOut() {
        existing(Role.DEVELOPER, true);
        service.update(id, new UpdateUserRequest(Role.DEVELOPER, false));
        verify(tokens).deleteByUserId(any());
    }

    @Test
    void resettingAPasswordSignsTheUserOut() {
        AppUser u = existing(Role.DEVELOPER, true);
        service.resetPassword(id, "another-long-password");
        assertTrue(encoder.matches("another-long-password", u.getPasswordHash()));
        verify(tokens).deleteByUserId(any());
    }
}
