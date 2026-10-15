package com.example.flags;

import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

final class PasswordPolicy {
    private PasswordPolicy() {}

    static final int MIN_LENGTH = 10;
    /** bcrypt only uses the first 72 bytes, so longer passwords would be silently cut. */
    static final int MAX_BYTES = 72;

    static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwords need at least " + MIN_LENGTH + " characters.");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwords can be at most " + MAX_BYTES + " bytes.");
    }
}
