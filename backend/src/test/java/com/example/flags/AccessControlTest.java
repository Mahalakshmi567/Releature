package com.example.flags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class AccessControlTest {

    private final AccessControl access = new AccessControl();

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void signInAs(Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone", null, role.authorities().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void developersChangeDevelopmentAndTestingButNotProduction() {
        signInAs(Role.DEVELOPER);
        assertTrue(access.canChangeEnvironment("development"));
        assertTrue(access.canChangeEnvironment("testing"));
        assertFalse(access.canChangeEnvironment("production"));

        ApiException e = assertThrows(ApiException.class, () -> access.requireChangeEnvironment("production"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertTrue(e.getMessage().contains("release manager"));
    }

    @Test
    void releaseManagersAndAdminsChangeProduction() {
        signInAs(Role.RELEASE_MANAGER);
        assertTrue(access.canChangeEnvironment("production"));
        signInAs(Role.ADMIN);
        assertTrue(access.canChangeEnvironment("production"));
    }

    @Test
    void viewersChangeNothing() {
        signInAs(Role.VIEWER);
        assertFalse(access.canChangeEnvironment("development"));
        assertFalse(access.canChangeEnvironment("production"));
    }

    @Test
    void changesThatReachProductionNeedReleaseManager() {
        signInAs(Role.DEVELOPER);
        assertThrows(ApiException.class, () -> access.requireProduction("Deleting a flag"));
        signInAs(Role.RELEASE_MANAGER);
        assertDoesNotThrow(() -> access.requireProduction("Deleting a flag"));
    }

    @Test
    void nobodySignedInMeansNoAccess() {
        assertFalse(access.has(Role.VIEWER));
        assertFalse(access.canChangeEnvironment("development"));
    }
}
