CREATE TABLE feature_flags (
    id            UUID PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    flag_key      VARCHAR(100)  NOT NULL UNIQUE,
    description   VARCHAR(500),
    data_type     VARCHAR(20)   NOT NULL CHECK (data_type IN ('BOOLEAN', 'STRING', 'NUMBER')),
    default_value VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL
);
