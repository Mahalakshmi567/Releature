package com.example.flags;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record UserResponse(UUID id, String username, Role role, boolean enabled, Instant createdAt) {
        static UserResponse from(AppUser u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getRole(), u.isEnabled(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{2,49}$",
                     message = "3 to 50 characters: lowercase letters, numbers, dot, dash, underscore")
            String username,
            @NotBlank String password,
            @NotNull Role role) {}

    public record UpdateUserRequest(@NotNull Role role, @NotNull Boolean enabled) {}

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String password) {}
}
