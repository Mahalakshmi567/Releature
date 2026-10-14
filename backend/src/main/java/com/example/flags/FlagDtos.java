package com.example.flags;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FlagDtos {
    private FlagDtos() {}

    // Every change needs a reason; it is stored in the audit history.

    public record CreateFlagRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "^[a-z][a-z0-9_]*$",
                     message = "Use lowercase letters, numbers and underscores, starting with a letter") String key,
            @NotNull FlagType dataType,
            @NotNull @Size(max = 1000) String defaultValue,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 500) String reason) {}

    /** Key and data type are fixed once a flag exists, because application code depends on them. */
    public record UpdateFlagRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Size(max = 1000) String defaultValue,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 500) String reason) {}

    /** Send a value, a rollout percentage, or both. Fields left out are not changed. */
    public record UpdateEnvironmentValueRequest(
            @Size(max = 1000) String value,
            @Min(value = 0, message = "Rollout must be between 0 and 100")
            @Max(value = 100, message = "Rollout must be between 0 and 100") Integer rolloutPercentage,
            @NotBlank @Size(max = 500) String reason) {}

    public record EnvironmentResponse(String key, String name) {}

    public record EnvironmentValueResponse(String environmentKey, String environmentName,
                                           String value, Instant updatedAt, int rolloutPercentage,
                                           int targetUserCount, int targetRuleCount) {}

    public record FlagResponse(UUID id, String name, String key, FlagType dataType,
                               String defaultValue, String description,
                               Instant createdAt, Instant updatedAt,
                               List<EnvironmentValueResponse> environments) {
        static FlagResponse from(Flag f, List<EnvironmentValueResponse> environments) {
            return new FlagResponse(f.getId(), f.getName(), f.getKey(), f.getDataType(),
                    f.getDefaultValue(), f.getDescription(), f.getCreatedAt(), f.getUpdatedAt(), environments);
        }
    }
}
