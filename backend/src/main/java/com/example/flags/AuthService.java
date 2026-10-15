package com.example.flags;

import com.example.flags.AuthDtos.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    /** Who a valid token belongs to, with their role as it is right now. */
    public record Principal(String username, Role role) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final Duration tokenLifetime;
    /** Compared against when the username is unknown, so a wrong username and a wrong password take equally long. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, AuthTokenRepository tokens, PasswordEncoder encoder,
                       @Value("${app.auth.token-hours:8}") long tokenHours) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.tokenLifetime = Duration.ofHours(tokenHours);
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public LoginResponse login(String username, String password) {
        String name = username == null ? "" : username.trim().toLowerCase();
        AppUser user = users.findByUsername(name).orElse(null);
        boolean passwordOk = encoder.matches(password == null ? "" : password,
                user != null ? user.getPasswordHash() : dummyHash);
        // Disabled accounts get the same answer as a wrong password.
        if (user == null || !user.isEnabled() || !passwordOk)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect username or password.");

        Instant now = Instant.now();
        tokens.deleteByExpiresAtBefore(now);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(tokenLifetime);
        tokens.save(new AuthToken(hash(token), user.getId(), user.getUsername(), now, expiresAt));
        return new LoginResponse(token, user.getUsername(), user.getRole(), expiresAt);
    }

    /** Returns the user and current role for a valid, unexpired token of an enabled account. */
    @Transactional(readOnly = true)
    public Optional<Principal> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return tokens.findActiveUser(hash(token), Instant.now())
                .map(u -> new Principal(u.getUsername(), u.getRole()));
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) tokens.deleteById(hash(token));
    }

    /** Changes the caller's own password and signs them out everywhere. */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in again."));
        // 400, not 401: a 401 would make the dashboard think the session expired.
        if (!encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        PasswordPolicy.validate(newPassword);
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        tokens.deleteByUserId(user.getId());
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
