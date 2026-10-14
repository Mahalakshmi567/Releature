package com.example.flags;

/** Result of evaluating one flag in one environment for one user. */
public record EvaluationResponse(String flagKey, String environment, String userId, FlagType dataType,
                                 String value, String reason, int rolloutPercentage, Integer bucket,
                                 String matchedRule) {}
