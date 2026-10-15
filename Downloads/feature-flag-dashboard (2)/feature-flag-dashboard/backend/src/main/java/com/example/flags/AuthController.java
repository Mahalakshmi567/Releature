package com.example.flags;

import com.example.flags.AuthDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    /** Only failed sign-ins count, so signing in and out with different accounts is never held up. */
    private final AttemptLimiter loginFailures = new AttemptLimiter(10, Duration.ofMinutes(15));
    private final AttemptLimiter registrations;

    public AuthController(AuthService auth,
                          @Value("${app.registration.max-per-hour-per-ip:10}") int maxRegistrationsPerHour) {
        this.auth = auth;
        this.registrations = new AttemptLimiter(maxRegistrationsPerHour, Duration.ofHours(1));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String client = http.getRemoteAddr();
        if (loginFailures.isBlocked(client))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-in attempts. Try again in a few minutes.");
        try {
            LoginResponse response = auth.login(request.username(), request.password());
            loginFailures.clear(client);
            return response;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) loginFailures.record(client);
            throw e;
        }
    }

    /** Public. Creates a viewer account and signs the new user in. Can be turned off with REGISTRATION_ENABLED=false. */
    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        if (!auth.registrationEnabled())
            throw new ApiException(HttpStatus.FORBIDDEN, "Registration is turned off. Ask an administrator for an account.");
        String client = http.getRemoteAddr();
        if (registrations.isBlocked(client))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many accounts were created from this address. Try again later.");
        LoginResponse response = auth.register(request.username(), request.password());
        registrations.record(client);
        return response;
    }

    /** Public, so the sign-in page knows whether to offer "Create an account". */
    @GetMapping("/registration")
    public RegistrationStatus registration() { return new RegistrationStatus(auth.registrationEnabled()); }

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
