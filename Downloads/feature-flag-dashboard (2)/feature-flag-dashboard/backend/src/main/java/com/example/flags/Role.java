package com.example.flags;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Roles from least to most powerful. Each role can do everything the roles before it can.
 *
 *  VIEWER           see flags, history and evaluation results
 *  DEVELOPER        create flags, edit names and descriptions, change development and testing
 *  RELEASE_MANAGER  also change production, change a flag's default value, delete flags
 *  ADMIN            also manage users and roles
 */
public enum Role {
    VIEWER, DEVELOPER, RELEASE_MANAGER, ADMIN;

    public boolean atLeast(Role other) { return ordinal() >= other.ordinal(); }

    /** The authorities this role carries: itself and every lower role, so hasRole('DEVELOPER') is true for an ADMIN. */
    public List<String> authorities() {
        return EnumSet.range(VIEWER, this).stream().map(r -> "ROLE_" + r.name()).toList();
    }

    /** The most powerful role among the given authorities, or VIEWER if there is none. */
    public static Role highest(Collection<? extends GrantedAuthority> authorities) {
        Role best = VIEWER;
        for (GrantedAuthority a : authorities) {
            for (Role r : values()) {
                if (("ROLE_" + r.name()).equals(a.getAuthority()) && r.atLeast(best)) best = r;
            }
        }
        return best;
    }
}
