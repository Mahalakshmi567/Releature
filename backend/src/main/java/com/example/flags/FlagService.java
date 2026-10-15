package com.example.flags;

import com.example.flags.FlagDtos.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FlagService {

    private final FlagRepository flags;
    private final EnvironmentRepository environments;
    private final FlagEnvironmentValueRepository values;
    private final TargetUserRepository targetUsers;
    private final TargetRuleRepository targetRules;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final AccessControl access;

    public FlagService(FlagRepository flags, EnvironmentRepository environments,
                       FlagEnvironmentValueRepository values, TargetUserRepository targetUsers,
                       TargetRuleRepository targetRules, AuditService audit,
                       ApplicationEventPublisher events, AccessControl access) {
        this.flags = flags;
        this.environments = environments;
        this.values = values;
        this.targetUsers = targetUsers;
        this.targetRules = targetRules;
        this.audit = audit;
        this.events = events;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<FlagResponse> list() {
        List<Flag> all = flags.findAll(Sort.by("name"));
        List<Environment> envs = environments.findAllByOrderBySortOrderAsc();
        List<FlagEnvironmentValue> vals = values.findByFlagIdIn(all.stream().map(Flag::getId).toList());
        Map<UUID, List<FlagEnvironmentValue>> byFlag = vals.stream()
                .collect(Collectors.groupingBy(FlagEnvironmentValue::getFlagId));
        Counts counts = counts(vals);
        return all.stream()
                .map(f -> toResponse(f, envs, byFlag.getOrDefault(f.getId(), List.of()), counts))
                .toList();
    }

    @Transactional(readOnly = true)
    public FlagResponse get(UUID id) { return respond(find(id)); }

    @Transactional
    public FlagResponse create(CreateFlagRequest r) {
        if (flags.existsByKey(r.key())) throw duplicate(r.key());
        Flag f = new Flag();
        f.setName(r.name().trim());
        f.setKey(r.key());
        f.setDataType(r.dataType());
        f.setDescription(blankToNull(r.description()));
        f.setDefaultValue(normalize(r.dataType(), r.defaultValue()));
        Flag saved;
        try {
            saved = flags.saveAndFlush(f);
        } catch (DataIntegrityViolationException e) {
            throw duplicate(r.key());
        }
        // The new flag starts with its default value in every environment.
        for (Environment env : environments.findAllByOrderBySortOrderAsc()) {
            FlagEnvironmentValue v = new FlagEnvironmentValue();
            v.setFlagId(saved.getId());
            v.setEnvironmentId(env.getId());
            v.setValue(saved.getDefaultValue());
            values.save(v);
        }
        audit.record(saved.getId(), saved.getKey(), saved.getName(), null, AuditAction.FLAG_CREATED,
                null, flagSnapshot(saved), r.reason());
        changed(saved.getKey(), allEnvironmentKeys());
        return respond(saved);
    }

    /** Updates name, description and default value. Existing environment values are left alone. */
    @Transactional
    public FlagResponse update(UUID id, UpdateFlagRequest r) {
        return update(id, r, null);
    }

    /** {@code rollbackOf} is the audit entry being undone when this edit is a rollback, otherwise null. */
    @Transactional
    public FlagResponse update(UUID id, UpdateFlagRequest r, UUID rollbackOf) {
        Flag f = find(id);
        Map<String, Object> before = flagSnapshot(f);
        // The default value is what users outside a rollout get, in every environment, so changing it reaches production.
        String newDefault = normalize(f.getDataType(), r.defaultValue());
        if (!newDefault.equals(f.getDefaultValue())) access.requireProduction("Changing a flag's default value");
        f.setName(r.name().trim());
        f.setDescription(blankToNull(r.description()));
        f.setDefaultValue(normalize(f.getDataType(), r.defaultValue()));
        Flag saved = flags.saveAndFlush(f);
        audit.record(saved.getId(), saved.getKey(), saved.getName(), null, AuditAction.FLAG_UPDATED,
                before, flagSnapshot(saved), r.reason(), rollbackOf);
        changed(saved.getKey(), allEnvironmentKeys());
        return respond(saved);
    }

    /** Changes the value and/or rollout percentage in exactly one environment. */
    @Transactional
    public FlagResponse setEnvironmentValue(UUID flagId, String envKey, String value, Integer rolloutPercentage,
                                            String reason) {
        return setEnvironmentValue(flagId, envKey, value, rolloutPercentage, reason, null);
    }

    @Transactional
    public FlagResponse setEnvironmentValue(UUID flagId, String envKey, String value, Integer rolloutPercentage,
                                            String reason, UUID rollbackOf) {
        access.requireChangeEnvironment(envKey);
        if (value == null && rolloutPercentage == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide a value, a rollout percentage, or both.");
        Flag f = find(flagId);
        Environment env = environments.findByKey(envKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + envKey + "' not found."));
        FlagEnvironmentValue row = values.findByFlagIdAndEnvironmentId(f.getId(), env.getId())
                .orElseGet(() -> {
                    FlagEnvironmentValue v = new FlagEnvironmentValue();
                    v.setFlagId(f.getId());
                    v.setEnvironmentId(env.getId());
                    v.setValue(f.getDefaultValue());
                    return v;
                });
        Map<String, Object> before = envSnapshot(row);
        if (value != null) row.setValue(normalize(f.getDataType(), value));
        if (rolloutPercentage != null) {
            if (rolloutPercentage < 0 || rolloutPercentage > 100)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Rollout percentage must be between 0 and 100.");
            row.setRolloutPercentage(rolloutPercentage);
        }
        values.saveAndFlush(row);
        audit.record(f.getId(), f.getKey(), f.getName(), env.getKey(), AuditAction.ENVIRONMENT_UPDATED,
                before, envSnapshot(row), reason, rollbackOf);
        changed(f.getKey(), List.of(env.getKey()));
        return respond(f);
    }

    @Transactional
    public void delete(UUID id, String reason) {
        access.requireProduction("Deleting a flag");
        Flag f = find(id);
        Map<String, Object> before = flagSnapshot(f);
        Map<UUID, String> envKeys = environments.findAll().stream()
                .collect(Collectors.toMap(Environment::getId, Environment::getKey));
        Map<String, Object> perEnvironment = new TreeMap<>();
        for (FlagEnvironmentValue v : values.findByFlagIdIn(List.of(id)))
            perEnvironment.put(envKeys.getOrDefault(v.getEnvironmentId(), "unknown"), envSnapshot(v));
        before.put("environments", perEnvironment);
        audit.record(f.getId(), f.getKey(), f.getName(), null, AuditAction.FLAG_DELETED, before, null, reason);
        changed(f.getKey(), allEnvironmentKeys());
        flags.delete(f);
    }

    /** Cached copies are dropped after this transaction commits (see FlagConfigInvalidationListener). */
    private void changed(String flagKey, List<String> envKeys) {
        events.publishEvent(new FlagConfigChangedEvent(flagKey, envKeys));
    }

    private List<String> allEnvironmentKeys() {
        return environments.findAllByOrderBySortOrderAsc().stream().map(Environment::getKey).toList();
    }

    private Flag find(UUID id) {
        return flags.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flag not found."));
    }

    static Map<String, Object> flagSnapshot(Flag f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", f.getName());
        m.put("key", f.getKey());
        m.put("description", f.getDescription());
        m.put("dataType", f.getDataType().name());
        m.put("defaultValue", f.getDefaultValue());
        return m;
    }

    static Map<String, Object> envSnapshot(FlagEnvironmentValue v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", v.getValue());
        m.put("rolloutPercentage", v.getRolloutPercentage());
        return m;
    }

    private FlagResponse respond(Flag f) {
        List<FlagEnvironmentValue> vals = values.findByFlagIdIn(List.of(f.getId()));
        return toResponse(f, environments.findAllByOrderBySortOrderAsc(), vals, counts(vals));
    }

    private record Counts(Map<UUID, Long> users, Map<UUID, Long> rules) {}

    private Counts counts(List<FlagEnvironmentValue> vals) {
        List<UUID> ids = vals.stream().map(FlagEnvironmentValue::getId).toList();
        if (ids.isEmpty()) return new Counts(Map.of(), Map.of());
        return new Counts(toMap(targetUsers.countByEnvironmentValueIds(ids)),
                toMap(targetRules.countByEnvironmentValueIds(ids)));
    }

    private static Map<UUID, Long> toMap(List<Object[]> rows) {
        Map<UUID, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put((UUID) r[0], (Long) r[1]);
        return m;
    }

    private static FlagResponse toResponse(Flag f, List<Environment> envs, List<FlagEnvironmentValue> vals,
                                           Counts counts) {
        Map<UUID, FlagEnvironmentValue> byEnv = vals.stream()
                .collect(Collectors.toMap(FlagEnvironmentValue::getEnvironmentId, v -> v));
        List<EnvironmentValueResponse> out = envs.stream().map(e -> {
            FlagEnvironmentValue v = byEnv.get(e.getId());
            return new EnvironmentValueResponse(e.getKey(), e.getName(),
                    v != null ? v.getValue() : f.getDefaultValue(),
                    v != null ? v.getUpdatedAt() : f.getUpdatedAt(),
                    v != null ? v.getRolloutPercentage() : 100,
                    v != null ? counts.users().getOrDefault(v.getId(), 0L).intValue() : 0,
                    v != null ? counts.rules().getOrDefault(v.getId(), 0L).intValue() : 0);
        }).toList();
        return FlagResponse.from(f, out);
    }

    private static String normalize(FlagType type, String value) {
        String v = value == null ? "" : value.trim();
        if (type == FlagType.BOOLEAN) {
            if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false"))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Value must be true or false.");
            return v.toLowerCase();
        }
        if (type == FlagType.NUMBER) {
            try { new BigDecimal(v); } catch (NumberFormatException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Value must be a number.");
            }
            return v;
        }
        return value == null ? "" : value;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static ApiException duplicate(String key) {
        return new ApiException(HttpStatus.CONFLICT, "A flag with the key '" + key + "' already exists.");
    }
}
