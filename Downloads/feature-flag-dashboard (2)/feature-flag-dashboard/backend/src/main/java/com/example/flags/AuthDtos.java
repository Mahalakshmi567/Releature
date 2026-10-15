package com.example.flags;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}

    public record MeResponse(String username, Role role) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    /** Self-registration. Letters may be upper-case here; the username is saved in lower-case. */
    public record RegisterRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,49}$",
                     message = "3 to 50 characters: letters, numbers, dot, dash or underscore")
            String username,
            @NotBlank String password) {}

    public record RegistrationStatus(boolean enabled) {}
}
