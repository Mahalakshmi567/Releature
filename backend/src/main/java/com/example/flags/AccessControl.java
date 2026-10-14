package com.example.flags;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Decides who may change what. Controllers use @PreAuthorize for the basic role; the services call this as well, so
 * the same rules also hold when a change arrives through another path, such as a rollback.
 */
@Component("access")
public class AccessControl {

    public static final String PRODUCTION = "production";

    public boolean has(Role role) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return false;
        String wanted = "ROLE_" + role.name();
        for (GrantedAuthority g : a.getAuthorities()) if (wanted.equals(g.getAuthority())) return true;
        return false;
    }

    public boolean canChangeEnvironment(String envKey) {
        return PRODUCTION.equals(envKey) ? has(Role.RELEASE_MANAGER) : has(Role.DEVELOPER);
    }

    public void requireChangeEnvironment(String envKey) {
        if (canChangeEnvironment(envKey)) return;
        throw new ApiException(HttpStatus.FORBIDDEN, PRODUCTION.equals(envKey)
                ? "Changing production requires the release manager role."
                : "Changing flags requires the developer role.");
    }

    /** For changes that reach production without naming it, such as a flag's default value or deleting a flag. */
    public void requireProduction(String what) {
        if (!has(Role.RELEASE_MANAGER))
            throw new ApiException(HttpStatus.FORBIDDEN, what + " affects production, which requires the release manager role.");
    }
}
