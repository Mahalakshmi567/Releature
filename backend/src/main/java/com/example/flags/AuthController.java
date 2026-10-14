package com.example.flags;

import com.example.flags.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) auth.logout(header.substring(7).trim());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName(), Role.highest(authentication.getAuthorities()));
    }

    /** Any signed-in user can change their own password. Everyone is signed out afterwards. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
    }
}
