-- Explicit user IDs that always get the environment value.
CREATE TABLE target_users (
    id                   UUID PRIMARY KEY,
    environment_value_id UUID         NOT NULL REFERENCES flag_environment_values (id) ON DELETE CASCADE,
    user_id              VARCHAR(200) NOT NULL,
    UNIQUE (environment_value_id, user_id)
);

-- Attribute rules, e.g. plan is one of (pro, enterprise). A user matching any rule is targeted.
CREATE TABLE target_rules (
    id                   UUID PRIMARY KEY,
    environment_value_id UUID        NOT NULL REFERENCES flag_environment_values (id) ON DELETE CASCADE,
    attribute            VARCHAR(50) NOT NULL
);
CREATE INDEX idx_target_rules_env_value ON target_rules (environment_value_id);

CREATE TABLE target_rule_values (
    rule_id     UUID         NOT NULL REFERENCES target_rules (id) ON DELETE CASCADE,
    match_value VARCHAR(100) NOT NULL
);
CREATE INDEX idx_target_rule_values_rule ON target_rule_values (rule_id);
