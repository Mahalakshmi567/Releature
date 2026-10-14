package com.example.flags;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class TargetingDtos {
    private TargetingDtos() {}

    /** "attribute is one of values", e.g. plan is one of [pro, enterprise]. */
    public record TargetingRuleDto(String attribute, List<String> values) {}

    public record TargetingDto(List<String> userIds, List<TargetingRuleDto> rules) {}

    public record UpdateTargetingRequest(List<String> userIds, List<TargetingRuleDto> rules,
                                         @NotBlank @Size(max = 500) String reason) {}

    public record EvaluateRequest(String userId, Map<String, String> attributes) {}
}
