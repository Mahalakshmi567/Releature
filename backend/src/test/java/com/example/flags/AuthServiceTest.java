package com.example.flags;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuthTokenRepository tokens = mock(AuthTokenRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost keeps the test fast
    private final AuthService service = new AuthService(users, tokens, encoder, 8);

    private AppUser givenUser(String username, String password, Role role) {
        AppUser u = new AppUser(username, encoder.encode(password), role);
        when(users.findByUsername(username)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void wrongPasswordIsRejected() {
        givenUser("admin", "correct-horse", Role.ADMIN);
        ApiException e = assertThrows(ApiException.class, () -> service.login("admin", "wrong"));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        verify(tokens, never()).save(any());
    }

    @Test
    void unknownUserGetsTheSameErrorAsAWrongPassword() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        givenUser("admin", "correct-horse", Role.ADMIN);
        ApiException unknown = assertThrows(ApiException.class, () -> service.login("ghost", "whatever"));
        ApiException wrong = assertThrows(ApiException.class, () -> service.login("admin", "wrong"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void disabledAccountCannotSignInEvenWithTheRightPassword() {
        AppUser u = givenUser("sam", "correct-horse", Role.DEVELOPER);
        u.setEnabled(false);
        ApiException e = assertThrows(ApiException.class, () -> service.login("sam", "correct-horse"));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        verify(tokens, never()).save(any());
    }

    @Test
    void usernameIsCaseInsensitive() {
        givenUser("admin", "correct-horse", Role.ADMIN);
        assertEquals("admin", service.login("  Admin ", "correct-horse").username());
    }

    @Test
    void loginReturnsTheUsersRole() {
        givenUser("rita", "correct-horse", Role.RELEASE_MANAGER);
        assertEquals(Role.RELEASE_MANAGER, service.login("rita", "correct-horse").role());
    }

    @Test
    void issuedTokenAuthenticatesWithTheCurrentRoleAndOnlyItsHashIsStored() {
        AppUser user = givenUser("admin", "correct-horse", Role.ADMIN);
        String token = service.login("admin", "correct-horse").token();

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokens).save(saved.capture());
        assertNotEquals(token, saved.getValue().getTokenHash());
        assertEquals(AuthService.hash(token), saved.getValue().getTokenHash());

        when(tokens.findActiveUser(eq(AuthService.hash(token)), any(Instant.class))).thenReturn(Optional.of(user));
        assertEquals(Optional.of(new AuthService.Principal("admin", Role.ADMIN)), service.authenticate(token));

        user.setRole(Role.VIEWER); // a role change applies on the next request
        assertEquals(Optional.of(new AuthService.Principal("admin", Role.VIEWER)), service.authenticate(token));
    }

    @Test
    void unknownExpiredOrDisabledTokensDoNotAuthenticate() {
        when(tokens.findActiveUser(any(), any(Instant.class))).thenReturn(Optional.empty());
        assertTrue(service.authenticate("some-token").isEmpty());
        assertTrue(service.authenticate("").isEmpty());
    }

    @Test
    void changingYourPasswordNeedsTheCurrentOneAndSignsYouOutEverywhere() {
        AppUser user = givenUser("sam", "old-password-1", Role.DEVELOPER);

        ApiException wrong = assertThrows(ApiException.class,
                () -> service.changePassword("sam", "not-the-password", "brand-new-password"));
        assertEquals(HttpStatus.BAD_REQUEST, wrong.getStatus()); // not 401, which the dashboard treats as an expired session

        service.changePassword("sam", "old-password-1", "brand-new-password");
        assertTrue(encoder.matches("brand-new-password", user.getPasswordHash()));
        verify(tokens).deleteByUserId(user.getId());
    }

    @Test
    void weakNewPasswordIsRejected() {
        givenUser("sam", "old-password-1", Role.DEVELOPER);
        ApiException e = assertThrows(ApiException.class, () -> service.changePassword("sam", "old-password-1", "short"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }
}
