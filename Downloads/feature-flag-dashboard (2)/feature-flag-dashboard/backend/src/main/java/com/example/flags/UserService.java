package com.example.flags;

import com.example.flags.UserDtos.CreateUserRequest;
import com.example.flags.UserDtos.UpdateUserRequest;
import com.example.flags.UserDtos.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder encoder;

    public UserService(AppUserRepository users, AuthTokenRepository tokens, PasswordEncoder encoder) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("username")).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest r) {
        String username = r.username().trim().toLowerCase();
        if (users.findByUsername(username).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "The username '" + username + "' is taken.");
        PasswordPolicy.validate(r.password());
        AppUser saved = users.save(new AppUser(username, encoder.encode(r.password()), r.role()));
        log.info("User '{}' created with role {} by {}", username, r.role(), AuditService.currentActor());
        return UserResponse.from(saved);
    }

    /** Changes a user's role or switches the account on or off. At least one active administrator must remain. */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest r) {
        AppUser u = find(id);
        boolean losesAdmin = u.getRole() == Role.ADMIN && u.isEnabled() && (r.role() != Role.ADMIN || !r.enabled());
        if (losesAdmin && users.countByRoleAndEnabled(Role.ADMIN, true) <= 1)
            throw new ApiException(HttpStatus.CONFLICT, "At least one active administrator is required.");

        Role before = u.getRole();
        boolean wasEnabled = u.isEnabled();
        u.setRole(r.role());
        u.setEnabled(r.enabled());
        users.save(u);
        if (!r.enabled()) tokens.deleteByUserId(id);
        log.info("User '{}' changed by {}: role {} -> {}, enabled {} -> {}", u.getUsername(),
                AuditService.currentActor(), before, r.role(), wasEnabled, r.enabled());
        return UserResponse.from(u);
    }

    /** An administrator sets a new password for someone else. That user is signed out everywhere. */
    @Transactional
    public void resetPassword(UUID id, String password) {
        AppUser u = find(id);
        PasswordPolicy.validate(password);
        u.setPasswordHash(encoder.encode(password));
        users.save(u);
        tokens.deleteByUserId(id);
        log.info("Password of user '{}' reset by {}", u.getUsername(), AuditService.currentActor());
    }

    private AppUser find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
