-- ============================================================
-- Landing page CMS-lite (Prompt 99): admin-editable hero/CTA config (a single row),
-- feature/FAQ/testimonial lists (sortOrder + isActive, read by the public landing page),
-- and a contact-us submissions table. Deliberately simple - this is marketing content,
-- not core product. LANDING_MANAGE/CONTACT_US_MANAGE permissions themselves are seeded by
-- AdminBootstrapSeeder (Java), not here - this project's catalog is seeder-owned, not
-- migration-owned, unlike ElderSphere's own equivalent migration.
-- ============================================================
CREATE TABLE landing_page_config (
    id                BIGSERIAL    PRIMARY KEY,
    hero_headline     VARCHAR(255),
    hero_subheadline  TEXT,
    hero_image_url    VARCHAR(500),
    cta_headline      VARCHAR(255),
    cta_description   TEXT,
    cta_button_text   VARCHAR(100),

    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT
);

CREATE TABLE landing_features (
    id                BIGSERIAL    PRIMARY KEY,
    title             VARCHAR(255),
    description       TEXT,
    icon_name         VARCHAR(100),
    sort_order        INTEGER,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT
);

CREATE TABLE landing_faqs (
    id                BIGSERIAL    PRIMARY KEY,
    question          TEXT,
    answer            TEXT,
    sort_order        INTEGER,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT
);

CREATE TABLE landing_testimonials (
    id                BIGSERIAL    PRIMARY KEY,
    author_name       VARCHAR(255),
    author_role       VARCHAR(255),
    content           TEXT,
    avatar_url        VARCHAR(500),
    rating            INTEGER,
    sort_order        INTEGER,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT
);

CREATE TABLE contact_us (
    id                BIGSERIAL    PRIMARY KEY,
    name              VARCHAR(255),
    email             VARCHAR(255),
    phone             VARCHAR(50),
    message           TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'NEW',

    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT
);

CREATE INDEX idx_landing_features_sort_order ON landing_features(sort_order);
CREATE INDEX idx_landing_faqs_sort_order ON landing_faqs(sort_order);
CREATE INDEX idx_landing_testimonials_sort_order ON landing_testimonials(sort_order);
CREATE INDEX idx_contact_us_status ON contact_us(status);
