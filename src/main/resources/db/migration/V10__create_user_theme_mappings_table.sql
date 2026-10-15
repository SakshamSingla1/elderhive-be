CREATE TABLE user_theme_mappings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    theme_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT uk_user_theme_mappings_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_theme_mappings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_theme_mappings_theme FOREIGN KEY (theme_id) REFERENCES color_themes (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_theme_mappings_theme_id ON user_theme_mappings (theme_id);
