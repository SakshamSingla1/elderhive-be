CREATE TABLE meta_config (
    id BIGSERIAL PRIMARY KEY,
    context VARCHAR(255) NOT NULL,
    data TEXT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT uk_meta_config_context UNIQUE (context)
);
