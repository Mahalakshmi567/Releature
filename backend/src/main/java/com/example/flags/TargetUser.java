package com.example.flags;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "target_users")
public class TargetUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "environment_value_id", nullable = false, updatable = false)
    private UUID environmentValueId;

    @Column(name = "user_id", nullable = false, length = 200)
    private String userId;

    protected TargetUser() {}

    public TargetUser(UUID environmentValueId, String userId) {
        this.environmentValueId = environmentValueId;
        this.userId = userId;
    }

    public UUID getId() { return id; }
    public UUID getEnvironmentValueId() { return environmentValueId; }
    public String getUserId() { return userId; }
}
