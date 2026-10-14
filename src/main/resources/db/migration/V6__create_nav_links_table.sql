CREATE TABLE nav_links (
    id BIGSERIAL PRIMARY KEY,
    nav_group VARCHAR(100),
    nav_index INTEGER NOT NULL DEFAULT 0,
    name VARCHAR(100) NOT NULL,
    path VARCHAR(255) NOT NULL,
    icon VARCHAR(100),
    required_permission VARCHAR(100),
    super_admin_only BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX idx_nav_links_status ON nav_links (status);
