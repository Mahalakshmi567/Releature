-- Existing users (the first account) become administrators so nobody is locked out by this upgrade.
ALTER TABLE app_users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'ADMIN'
    CHECK (role IN ('VIEWER', 'DEVELOPER', 'RELEASE_MANAGER', 'ADMIN'));

ALTER TABLE app_users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
