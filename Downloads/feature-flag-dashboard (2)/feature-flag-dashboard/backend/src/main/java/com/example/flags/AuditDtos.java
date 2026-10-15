package com.example.flags;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {}

    /** The earlier entry a rollback entry restored. */
    public record RolledBackEntry(UUID id, String actor, Instant createdAt) {}

    public record AuditEntryResponse(UUID id, UUID flagId, String flagKey, String flagName, String environment,
                                     AuditAction action, String actor, String reason,
                                     JsonNode previous, JsonNode newConfig, Instant createdAt,
                                     RolledBackEntry rollbackOf) {}

    public record AuditPage(List<AuditEntryResponse> items, int page, int size, long total) {}

    /** What a rollback would do right now. `current` and `restoreTo` are the configurations to compare. */
    public record RollbackPreview(UUID entryId, boolean possible, String problem, String flagKey, String flagName,
                                  String environment, AuditAction action, JsonNode current, JsonNode restoreTo,
                                  boolean changedSince) {}

    public record RollbackRequest(@NotBlank @Size(max = 500) String reason) {}
}
