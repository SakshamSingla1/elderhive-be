-- Both FKs cascade: a bookmark is meaningless once the family user or the caretaker profile
-- it points at is gone. Unique on the pair - the same caretaker can't be favorited twice by
-- the same user. family_user_id is explicitly indexed - every read here (list, exists,
-- delete) filters by it.
CREATE TABLE favorite_caretakers (
    id BIGSERIAL PRIMARY KEY,
    family_user_id BIGINT NOT NULL,
    caretaker_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_favorite_caretakers_family_user FOREIGN KEY (family_user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_caretakers_caretaker FOREIGN KEY (caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uk_favorite_caretakers_family_user_caretaker UNIQUE (family_user_id, caretaker_id)
);

CREATE INDEX idx_favorite_caretakers_family_user_id ON favorite_caretakers (family_user_id);
