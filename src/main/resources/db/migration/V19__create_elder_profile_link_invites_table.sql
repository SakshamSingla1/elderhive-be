-- Cascades with its elder profile - an invite is meaningless once the profile it's for is
-- gone. token is globally unique (not scoped to the profile) since it's the sole credential
-- an unauthenticated emailed link carries. No FK to users for invited_email/invited_phone -
-- unlike FamilyElderLink, the invited person may not have an account yet; matching happens
-- at accept time against the authenticated caller's own email/phone.
CREATE TABLE elder_profile_link_invites (
    id BIGSERIAL PRIMARY KEY,
    elder_profile_id BIGINT NOT NULL,
    invited_email VARCHAR(255),
    invited_phone VARCHAR(50),
    invited_role VARCHAR(50) NOT NULL,
    relationship_label VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    token VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_elder_profile_link_invites_elder_profile FOREIGN KEY (elder_profile_id)
        REFERENCES elder_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uk_elder_profile_link_invites_token UNIQUE (token)
);

CREATE INDEX idx_elder_profile_link_invites_elder_profile_id ON elder_profile_link_invites (elder_profile_id);
