-- Both FKs cascade: a link is meaningless once its elder profile is gone, and a family
-- member losing their account shouldn't leave a dangling co-management row behind either.
-- The unique pair prevents the same family member being linked to the same profile twice
-- under two different relationship labels. elder_profile_id is explicitly indexed per this
-- prompt's own requirement - FamilyElderLinkService#findAllByElderProfileId (Phase 11's
-- emergency fan-out depends on it) always queries by this column.
CREATE TABLE family_elder_links (
    id BIGSERIAL PRIMARY KEY,
    elder_profile_id BIGINT NOT NULL,
    family_user_id BIGINT NOT NULL,
    relationship_label VARCHAR(255),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_family_elder_links_elder_profile FOREIGN KEY (elder_profile_id)
        REFERENCES elder_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_family_elder_links_family_user FOREIGN KEY (family_user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_family_elder_links_profile_family_user UNIQUE (elder_profile_id, family_user_id)
);

CREATE INDEX idx_family_elder_links_elder_profile_id ON family_elder_links (elder_profile_id);
