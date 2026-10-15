package com.example.flags;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}

    public record MeResponse(String username, Role role) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
}
