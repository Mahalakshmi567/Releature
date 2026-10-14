package com.example.flags;

import com.example.flags.AuditDtos.RollbackPreview;
import com.example.flags.FlagDtos.UpdateFlagRequest;
import com.example.flags.TargetingDtos.TargetingDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Restores the configuration a flag had before a given change.
 *
 * This is a configuration rollback only. It puts values, rollout percentages, targeting or flag settings back as they
 * were. It cannot undo anything the feature already did while it was on, such as records created for users.
 *
 * The restore goes through the normal services, so it is validated like any edit, the Redis cache is invalidated, and
 * it is recorded as a NEW audit entry that points at the entry it undoes. The history itself is never edited.
 */
@Service
public class RollbackService {

    /** Created and deleted flags are not restorable: a deletion also removes targeting, which the history does not keep. */
    private static final Set<AuditAction> SUPPORTED =
            EnumSet.of(AuditAction.FLAG_UPDATED, AuditAction.ENVIRONMENT_UPDATED, AuditAction.TARGETING_UPDATED);

    private record Plan(AuditLogEntry entry, RollbackPreview preview, JsonNode target, HttpStatus problemStatus) {}

    private final AuditRepository audit;
    private final FlagRepository flags;
    private final EnvironmentRepository environments;
    private final FlagEnvironmentValueRepository values;
    private final FlagService flagService;
    private final TargetingService targetingService;
    private final ObjectMapper mapper;

    public RollbackService(AuditRepository audit, FlagRepository flags, EnvironmentRepository environments,
                           FlagEnvironmentValueRepository values, FlagService flagService,
                           TargetingService targetingService, ObjectMapper mapper) {
        this.audit = audit;
        this.flags = flags;
        this.environments = environments;
        this.values = values;
        this.flagService = flagService;
        this.targetingService = targetingService;
        this.mapper = mapper;
    }

    /** Shows what would change if the entry were rolled back now, or why it can't be. */
    @Transactional(readOnly = true)
    public RollbackPreview preview(UUID entryId) { return plan(entryId).preview(); }

    /** Restores the configuration from before the entry's change. */
    @Transactional
    public void rollback(UUID entryId, String reason) {
        String cleanReason = AuditService.cleanReason(reason);
        Plan plan = plan(entryId);
        if (!plan.preview().possible()) throw new ApiException(plan.problemStatus(), plan.preview().problem());

        AuditLogEntry e = plan.entry();
        JsonNode t = plan.target();
        switch (e.getAction()) {
            case FLAG_UPDATED -> flagService.update(e.getFlagId(),
                    new UpdateFlagRequest(text(t, "name"), text(t, "defaultValue"), text(t, "description"), cleanReason),
                    e.getId());
            case ENVIRONMENT_UPDATED -> flagService.setEnvironmentValue(e.getFlagId(), e.getEnvironmentKey(),
                    text(t, "value"), t.path("rolloutPercentage").asInt(100), cleanReason, e.getId());
            case TARGETING_UPDATED -> targetingService.replace(e.getFlagId(), e.getEnvironmentKey(),
                    toTargeting(t), cleanReason, e.getId());
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Changes of this kind can't be rolled back.");
        }
    }

    private Plan plan(UUID entryId) {
        AuditLogEntry e = audit.findById(entryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Audit entry not found."));
        Flag flag = flags.findById(e.getFlagId()).orElse(null);

        JsonNode target = null;
        JsonNode current = null;
        boolean changedSince = false;
        String problem = null;
        HttpStatus status = null;

        if (!SUPPORTED.contains(e.getAction())) {
            problem = "Changes of this kind can't be rolled back.";
            status = HttpStatus.BAD_REQUEST;
        } else if (e.getPreviousConfig() == null) {
            problem = "This entry has no earlier configuration to restore.";
            status = HttpStatus.BAD_REQUEST;
        } else if (flag == null) {
            problem = "The flag no longer exists, so its configuration can't be restored.";
            status = HttpStatus.CONFLICT;
        } else {
            target = parse(e.getPreviousConfig());
            current = currentConfig(e, flag);
            if (current == null) {
                problem = "The current configuration could not be found.";
                status = HttpStatus.CONFLICT;
            } else if (target.equals(current)) {
                problem = "The configuration already matches what it was before this change.";
                status = HttpStatus.CONFLICT;
            } else {
                // Restoring replaces the whole configuration, so it also undoes anything changed after this entry.
                changedSince = e.getNewConfig() != null && !current.equals(parse(e.getNewConfig()));
            }
        }
        RollbackPreview preview = new RollbackPreview(e.getId(), problem == null, problem, e.getFlagKey(),
                flag != null ? flag.getName() : e.getFlagName(), e.getEnvironmentKey(), e.getAction(),
                current, target, changedSince);
        return new Plan(e, preview, target, status);
    }

    /** The configuration as it is now, in the same shape the audit history stores. */
    private JsonNode currentConfig(AuditLogEntry e, Flag flag) {
        switch (e.getAction()) {
            case FLAG_UPDATED:
                return mapper.<JsonNode>valueToTree(FlagService.flagSnapshot(flag));
            case ENVIRONMENT_UPDATED:
                return environments.findByKey(e.getEnvironmentKey())
                        .flatMap(env -> values.findByFlagIdAndEnvironmentId(flag.getId(), env.getId()))
                        .map(v -> mapper.<JsonNode>valueToTree(FlagService.envSnapshot(v)))
                        .orElse(null);
            case TARGETING_UPDATED:
                try {
                    return mapper.<JsonNode>valueToTree(targetingService.get(flag.getId(), e.getEnvironmentKey()));
                } catch (ApiException ex) {
                    return null;
                }
            default:
                return null;
        }
    }

    private TargetingDto toTargeting(JsonNode t) {
        try {
            return mapper.treeToValue(t, TargetingDto.class);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "The stored configuration could not be read.");
        }
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored audit entry is not valid JSON", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
