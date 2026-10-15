package com.example.flags;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One row of the append-only audit history. Entries are never updated or deleted. */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_id", nullable = false, updatable = false)
    private UUID flagId;

    @Column(name = "flag_key", nullable = false, updatable = false, length = 100)
    private String flagKey;

    @Column(name = "flag_name", nullable = false, updatable = false, length = 100)
    private String flagName;

    @Column(name = "environment_key", updatable = false, length = 30)
    private String environmentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private AuditAction action;

    @Column(nullable = false, updatable = false, length = 100)
    private String actor;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "previous_config", updatable = false)
    private String previousConfig;

    @Column(name = "new_config", updatable = false)
    private String newConfig;

    /** Set when this entry is a rollback: the id of the entry whose change it restored. */
    @Column(name = "rollback_of", updatable = false)
    private UUID rollbackOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLogEntry() {}

    public AuditLogEntry(UUID flagId, String flagKey, String flagName, String environmentKey, AuditAction action,
                         String actor, String reason, String previousConfig, String newConfig, UUID rollbackOf) {
        this.flagId = flagId;
        this.flagKey = flagKey;
        this.flagName = flagName;
        this.environmentKey = environmentKey;
        this.action = action;
        this.actor = actor;
        this.reason = reason;
        this.previousConfig = previousConfig;
        this.newConfig = newConfig;
        this.rollbackOf = rollbackOf;
    }

    public UUID getId() { return id; }
    public UUID getFlagId() { return flagId; }
    public String getFlagKey() { return flagKey; }
    public String getFlagName() { return flagName; }
    public String getEnvironmentKey() { return environmentKey; }
    public AuditAction getAction() { return action; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public String getPreviousConfig() { return previousConfig; }
    public String getNewConfig() { return newConfig; }
    public UUID getRollbackOf() { return rollbackOf; }
    public Instant getCreatedAt() { return createdAt; }
}
