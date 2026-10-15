package com.example.flags;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads a flag's evaluation configuration from PostgreSQL. Used on a cache miss. */
@Component
public class FlagConfigLoader {

    private final FlagRepository flags;
    private final EnvironmentRepository environments;
    private final FlagEnvironmentValueRepository values;
    private final TargetUserRepository targetUsers;
    private final TargetRuleRepository targetRules;

    public FlagConfigLoader(FlagRepository flags, EnvironmentRepository environments,
                            FlagEnvironmentValueRepository values, TargetUserRepository targetUsers,
                            TargetRuleRepository targetRules) {
        this.flags = flags;
        this.environments = environments;
        this.values = values;
        this.targetUsers = targetUsers;
        this.targetRules = targetRules;
    }

    @Transactional(readOnly = true)
    public FlagConfigSnapshot load(String envKey, String flagKey) {
        Flag flag = flags.findByKey(flagKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flag '" + flagKey + "' not found."));
        Environment env = environments.findByKey(envKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + envKey + "' not found."));
        FlagEnvironmentValue cfg = values.findByFlagIdAndEnvironmentId(flag.getId(), env.getId()).orElse(null);
        if (cfg == null) {
            return new FlagConfigSnapshot(flag.getKey(), env.getKey(), flag.getDataType(), flag.getDefaultValue(),
                    flag.getDefaultValue(), 100, Set.of(), List.of());
        }
        Set<String> users = targetUsers.findByEnvironmentValueId(cfg.getId()).stream()
                .map(TargetUser::getUserId).collect(Collectors.toSet());
        List<FlagConfigSnapshot.Rule> rules = targetRules.findByEnvironmentValueId(cfg.getId()).stream()
                .map(r -> new FlagConfigSnapshot.Rule(r.getAttribute(), List.copyOf(r.getMatchValues())))
                .toList();
        return new FlagConfigSnapshot(flag.getKey(), env.getKey(), flag.getDataType(), flag.getDefaultValue(),
                cfg.getValue(), cfg.getRolloutPercentage(), users, rules);
    }
}
