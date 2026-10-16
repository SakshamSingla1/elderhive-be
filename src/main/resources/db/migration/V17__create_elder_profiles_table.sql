-- family_user_id cascades: deleting the owning family member's account deletes the
-- profiles they created (Prompt 62's co-managing family members don't exist yet, so a
-- deleted owner currently means nobody else can reach this profile anyway). elder_user_id
-- SETs NULL instead: the elder losing their own login shouldn't destroy a profile the
-- family member still manages - it just goes back to "not yet linked".
CREATE TABLE elder_profiles (
    id BIGSERIAL PRIMARY KEY,
    family_user_id BIGINT,
    elder_user_id BIGINT,
    full_name VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(20),
    medical_conditions TEXT,
    address TEXT,
    emergency_contact_name VARCHAR(255),
    emergency_contact_phone VARCHAR(50),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_elder_profiles_family_user FOREIGN KEY (family_user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_elder_profiles_elder_user FOREIGN KEY (elder_user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    -- "set once the elder links their own login" implies one profile per elder login -
    -- Postgres UNIQUE allows any number of NULLs through, so this only ever rejects a
    -- SECOND profile claiming the same already-linked elder_user_id.
    CONSTRAINT uk_elder_profiles_elder_user_id UNIQUE (elder_user_id)
);

CREATE INDEX idx_elder_profiles_family_user_id ON elder_profiles (family_user_id);
CREATE INDEX idx_elder_profiles_elder_user_id ON elder_profiles (elder_user_id);
