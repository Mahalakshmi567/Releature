package com.example.flags;

import java.util.List;
import java.util.Set;

/** Everything needed to evaluate one flag in one environment. This is what gets cached. */
public record FlagConfigSnapshot(String flagKey, String environmentKey, FlagType dataType, String defaultValue,
                                 String envValue, int rolloutPercentage, Set<String> targetUserIds,
                                 List<Rule> rules) {

    /** "attribute is one of values". */
    public record Rule(String attribute, List<String> values) {}

    public FlagConfigSnapshot {
        targetUserIds = targetUserIds == null ? Set.of() : targetUserIds;
        rules = rules == null ? List.of() : rules;
    }
}
