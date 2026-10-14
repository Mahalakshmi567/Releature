package com.example.flags;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 100)
    private String username;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected AuthToken() {}

    public AuthToken(String tokenHash, UUID userId, String username, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.username = username;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public Instant getExpiresAt() { return expiresAt; }
}
