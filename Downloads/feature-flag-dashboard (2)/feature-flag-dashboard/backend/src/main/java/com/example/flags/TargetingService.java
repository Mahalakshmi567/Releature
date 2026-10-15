package com.example.flags;

import com.example.flags.TargetingDtos.TargetingDto;
import com.example.flags.TargetingDtos.TargetingRuleDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TargetingService {

    static final int MAX_USERS = 1000;
    static final int MAX_RULES = 20;
    static final int MAX_VALUES = 50;
    private static final Pattern ATTRIBUTE = Pattern.compile("^[a-z][a-z0-9_]{0,49}$");
    private static final Comparator<TargetingRuleDto> RULE_ORDER =
            Comparator.comparing(TargetingRuleDto::attribute).thenComparing(r -> String.join(",", r.values()));

    private final FlagRepository flags;
    private final EnvironmentRepository environments;
    private final FlagEnvironmentValueRepository values;
    private final TargetUserRepository users;
    private final TargetRuleRepository rules;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final AccessControl access;

    public TargetingService(FlagRepository flags, EnvironmentRepository environments,
                            FlagEnvironmentValueRepository values, TargetUserRepository users,
                            TargetRuleRepository rules, AuditService audit,
                            ApplicationEventPublisher events, AccessControl access) {
        this.flags = flags;
        this.environments = environments;
        this.values = values;
        this.users = users;
        this.rules = rules;
        this.audit = audit;
        this.events = events;
        this.access = access;
    }

    private record Scope(Flag flag, Environment env, UUID valueId) {}

    @Transactional(readOnly = true)
    public TargetingDto get(UUID flagId, String envKey) {
        return read(scope(flagId, envKey).valueId());
    }

    /** Replaces the whole targeting configuration of one flag in one environment and records the change. */
    @Transactional
    public TargetingDto replace(UUID flagId, String envKey, TargetingDto dto, String reason) {
        return replace(flagId, envKey, dto, reason, null);
    }

    /** {@code rollbackOf} is the audit entry being undone when this change is a rollback, otherwise null. */
    @Transactional
    public TargetingDto replace(UUID flagId, String envKey, TargetingDto dto, String reason, UUID rollbackOf) {
        access.requireChangeEnvironment(envKey);
        Scope s = scope(flagId, envKey);
        TargetingDto before = read(s.valueId());
        List<String> userIds = cleanUsers(dto.userIds());
        List<TargetingRuleDto> cleanRules = cleanRules(dto.rules());

        users.deleteByEnvironmentValueId(s.valueId());
        rules.deleteByEnvironmentValueId(s.valueId());
        // Deletes must hit the database before the inserts below, or re-adding the same user hits the unique key.
        users.flush();
        rules.flush();

        for (String u : userIds) users.save(new TargetUser(s.valueId(), u));
        for (TargetingRuleDto r : cleanRules) rules.save(new TargetRule(s.valueId(), r.attribute(), r.values()));

        TargetingDto after = new TargetingDto(userIds.stream().sorted().toList(), cleanRules);
        audit.record(s.flag().getId(), s.flag().getKey(), s.flag().getName(), s.env().getKey(),
                AuditAction.TARGETING_UPDATED, before, after, reason, rollbackOf);
        events.publishEvent(new FlagConfigChangedEvent(s.flag().getKey(), List.of(s.env().getKey())));
        return after;
    }

    private TargetingDto read(UUID valueId) {
        List<String> userIds = users.findByEnvironmentValueId(valueId).stream()
                .map(TargetUser::getUserId).sorted().toList();
        List<TargetingRuleDto> out = rules.findByEnvironmentValueId(valueId).stream()
                .map(r -> new TargetingRuleDto(r.getAttribute(),
                        r.getMatchValues().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()))
                .sorted(RULE_ORDER)
                .toList();
        return new TargetingDto(userIds, out);
    }

    private Scope scope(UUID flagId, String envKey) {
        Flag flag = flags.findById(flagId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flag not found."));
        Environment env = environments.findByKey(envKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + envKey + "' not found."));
        UUID valueId = values.findByFlagIdAndEnvironmentId(flagId, env.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flag has no configuration in this environment."))
                .getId();
        return new Scope(flag, env, valueId);
    }

    private static List<String> cleanUsers(List<String> input) {
        Set<String> out = new LinkedHashSet<>();
        if (input != null) {
            for (String raw : input) {
                String u = raw == null ? "" : raw.trim();
                if (u.isEmpty()) continue;
                if (u.length() > 200) throw bad("User IDs can be at most 200 characters.");
                out.add(u);
            }
        }
        if (out.size() > MAX_USERS) throw bad("At most " + MAX_USERS + " user IDs per environment.");
        return List.copyOf(out);
    }

    private static List<TargetingRuleDto> cleanRules(List<TargetingRuleDto> input) {
        List<TargetingRuleDto> out = new ArrayList<>();
        if (input != null) {
            for (TargetingRuleDto r : input) {
                String attribute = r == null || r.attribute() == null ? "" : r.attribute().trim().toLowerCase();
                if (!ATTRIBUTE.matcher(attribute).matches())
                    throw bad("Attribute names use lowercase letters, numbers and underscores, e.g. plan or country.");
                Map<String, String> unique = new LinkedHashMap<>();
                if (r.values() != null) {
                    for (String raw : r.values()) {
                        String v = raw == null ? "" : raw.trim();
                        if (v.isEmpty()) continue;
                        if (v.length() > 100) throw bad("Rule values can be at most 100 characters.");
                        unique.putIfAbsent(v.toLowerCase(), v);
                    }
                }
                if (unique.isEmpty()) throw bad("The rule for '" + attribute + "' needs at least one value.");
                if (unique.size() > MAX_VALUES) throw bad("At most " + MAX_VALUES + " values per rule.");
                out.add(new TargetingRuleDto(attribute,
                        unique.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
            }
        }
        if (out.size() > MAX_RULES) throw bad("At most " + MAX_RULES + " rules per environment.");
        out.sort(RULE_ORDER);
        return List.copyOf(out);
    }

    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
