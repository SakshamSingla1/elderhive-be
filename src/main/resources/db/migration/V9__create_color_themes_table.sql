CREATE TABLE color_themes (
    id BIGSERIAL PRIMARY KEY,
    theme_name VARCHAR(255),
    palette TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT
);
