package com.example.flags;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void eachRoleCarriesAllLowerRoles() {
        assertEquals(List.of("ROLE_VIEWER"), Role.VIEWER.authorities());
        assertEquals(List.of("ROLE_VIEWER", "ROLE_DEVELOPER"), Role.DEVELOPER.authorities());
        assertTrue(Role.ADMIN.authorities().containsAll(
                List.of("ROLE_VIEWER", "ROLE_DEVELOPER", "ROLE_RELEASE_MANAGER", "ROLE_ADMIN")));
    }

    @Test
    void highestPicksTheMostPowerfulAuthority() {
        assertEquals(Role.RELEASE_MANAGER, Role.highest(
                Role.RELEASE_MANAGER.authorities().stream().map(SimpleGrantedAuthority::new).toList()));
        assertEquals(Role.VIEWER, Role.highest(List.of()));
    }

    @Test
    void atLeastFollowsTheOrder() {
        assertTrue(Role.ADMIN.atLeast(Role.RELEASE_MANAGER));
        assertTrue(Role.DEVELOPER.atLeast(Role.DEVELOPER));
        assertFalse(Role.DEVELOPER.atLeast(Role.RELEASE_MANAGER));
    }
}
