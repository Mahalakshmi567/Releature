package com.example.flags;

import java.time.Instant;

/**
 * What dashboards are told when a flag changes. It says what changed and who changed it, never the configuration
 * itself; dashboards read the flags again to get the new values.
 */
public record ChangeNotification(String id, String flagKey, String flagName, String environment,
                                 AuditAction action, String actor, String reason, Instant at, boolean rollback) {}
