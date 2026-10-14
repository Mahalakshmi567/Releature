-- 100 = everyone gets the environment value, which is how every flag behaved before rollouts existed.
ALTER TABLE flag_environment_values
    ADD COLUMN rollout_percentage INT NOT NULL DEFAULT 100
    CHECK (rollout_percentage BETWEEN 0 AND 100);
