package com.example.flags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first account, an administrator, when the database has no users. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminSeeder(AppUserRepository users, PasswordEncoder encoder,
                       @Value("${app.admin.username}") String username,
                       @Value("${app.admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        users.save(new AppUser(username.trim().toLowerCase(), encoder.encode(password), Role.ADMIN));
        log.info("Created the first user '{}' with the ADMIN role.", username.trim().toLowerCase());
        if ("admin123".equals(password))
            log.warn("The first user has the default password. Change it from the dashboard, or set ADMIN_PASSWORD before the first start.");
    }
}
