-- Users who can sign in to the dashboard.
CREATE TABLE app_users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Login tokens. Only a SHA-256 hash of the token is stored, never the token itself.
CREATE TABLE auth_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    username   VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_auth_tokens_expires ON auth_tokens (expires_at);

-- Append-only history of every flag change. No foreign key to feature_flags, so history survives deleting a flag.
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY,
    flag_id         UUID         NOT NULL,
    flag_key        VARCHAR(100) NOT NULL,
    flag_name       VARCHAR(100) NOT NULL,
    environment_key VARCHAR(30),
    action          VARCHAR(30)  NOT NULL,
    actor           VARCHAR(100) NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    previous_config TEXT,
    new_config      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_flag_key ON audit_log (flag_key);

CREATE FUNCTION audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_change
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
