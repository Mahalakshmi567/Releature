package com.example.flags;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
    void deleteByExpiresAtBefore(Instant cutoff);

    /** Signs a user out everywhere, for example after a password change. */
    void deleteByUserId(UUID userId);

    /** The user a valid token belongs to, read fresh so role changes and disabling apply on the very next request. */
    @Query("select u from AppUser u, AuthToken t where t.tokenHash = :hash and t.userId = u.id "
            + "and t.expiresAt > :now and u.enabled = true")
    Optional<AppUser> findActiveUser(@Param("hash") String hash, @Param("now") Instant now);
}
