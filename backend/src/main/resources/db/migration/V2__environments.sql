CREATE TABLE environments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    env_key    VARCHAR(30) NOT NULL UNIQUE,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO environments (env_key, name, sort_order) VALUES
    ('development', 'Development', 1),
    ('testing',     'Testing',     2),
    ('production',  'Production',  3);

-- One value per flag per environment, so changing one never touches the others.
CREATE TABLE flag_environment_values (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id        UUID          NOT NULL REFERENCES feature_flags (id) ON DELETE CASCADE,
    environment_id UUID          NOT NULL REFERENCES environments (id),
    env_value      VARCHAR(1000) NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (flag_id, environment_id)
);

-- Existing flags start with their default value in every environment.
INSERT INTO flag_environment_values (flag_id, environment_id, env_value)
SELECT f.id, e.id, f.default_value FROM feature_flags f CROSS JOIN environments e;
