package com.example.flags;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class EvaluationService {

    private final FlagConfigService configs;

    public EvaluationService(FlagConfigService configs) { this.configs = configs; }

    /**
     * Decision order, first match wins:
     *  1. The user ID is on the explicit target list      -> environment value (EXPLICIT_USER)
     *  2. A targeting rule matches the user's attributes  -> environment value (ATTRIBUTE_RULE)
     *  3. Percentage rollout: inside the rollout gets the environment value, outside gets the flag's
     *     default value (FULLY_ROLLED_OUT / NOT_ROLLED_OUT / IN_ROLLOUT / OUTSIDE_ROLLOUT)
     * Targeting overrides the rollout percentage, even at 0%.
     *
     * The configuration comes from Redis when cached, so a normal evaluation does not touch the database.
     */
    public EvaluationResponse evaluate(String envKey, String flagKey, String userId, Map<String, String> attributes) {
        FlagConfigSnapshot cfg = configs.get(envKey, flagKey);

        int percentage = cfg.rolloutPercentage();
        String user = userId == null ? "" : userId.trim();
        Integer bucket = user.isEmpty() ? null : Bucketing.bucket(cfg.flagKey(), user);

        Map<String, String> attrs = new HashMap<>();
        if (attributes != null) {
            attributes.forEach((k, v) -> {
                if (k != null && v != null) attrs.put(k.trim().toLowerCase(), v.trim());
            });
        }

        String value = null;
        String reason = null;
        String matchedRule = null;

        if (!user.isEmpty() && cfg.targetUserIds().contains(user)) {
            value = cfg.envValue();
            reason = "EXPLICIT_USER";
        } else {
            for (FlagConfigSnapshot.Rule rule : cfg.rules()) {
                if (TargetingMatcher.matches(rule.attribute(), rule.values(), attrs)) {
                    value = cfg.envValue();
                    reason = "ATTRIBUTE_RULE";
                    matchedRule = rule.attribute() + " is one of " + String.join(", ", rule.values());
                    break;
                }
            }
        }

        if (reason == null) {
            if (percentage >= 100) {
                value = cfg.envValue();
                reason = "FULLY_ROLLED_OUT";
            } else if (percentage <= 0) {
                value = cfg.defaultValue();
                reason = "NOT_ROLLED_OUT";
            } else {
                if (bucket == null)
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "userId is required when a flag is partially rolled out.");
                boolean inside = bucket < percentage;
                value = inside ? cfg.envValue() : cfg.defaultValue();
                reason = inside ? "IN_ROLLOUT" : "OUTSIDE_ROLLOUT";
            }
        }
        return new EvaluationResponse(cfg.flagKey(), cfg.environmentKey(), user.isEmpty() ? null : user,
                cfg.dataType(), value, reason, percentage, bucket, matchedRule);
    }
}
