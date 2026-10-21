-- Admin-manageable service catalog data - deliberately NOT a hardcoded Java enum
-- (IMPLEMENTATION_PLAN.md section 2, point 3). name is unique so the same category can't
-- be created twice under a different id.
CREATE TABLE service_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    icon VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT uk_service_categories_name UNIQUE (name)
);
