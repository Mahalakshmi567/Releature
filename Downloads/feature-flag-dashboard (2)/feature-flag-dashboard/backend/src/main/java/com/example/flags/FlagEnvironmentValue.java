package com.example.flags;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flag_environment_values")
public class FlagEnvironmentValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_id", nullable = false, updatable = false)
    private UUID flagId;

    @Column(name = "environment_id", nullable = false, updatable = false)
    private UUID environmentId;

    @Column(name = "env_value", nullable = false, length = 1000)
    private String value;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage = 100;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getFlagId() { return flagId; }
    public void setFlagId(UUID flagId) { this.flagId = flagId; }
    public UUID getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public void setRolloutPercentage(int rolloutPercentage) { this.rolloutPercentage = rolloutPercentage; }
    public Instant getUpdatedAt() { return updatedAt; }
}
