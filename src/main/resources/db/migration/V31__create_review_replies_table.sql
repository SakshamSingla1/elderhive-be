-- Both FKs cascade - a reply referencing a now-deleted review or caretaker profile is
-- meaningless, same "owning relationship -> CASCADE" precedent used throughout this
-- schema (see V30's own reviews table for the identical reasoning).
CREATE TABLE review_replies (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    caretaker_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT uk_review_replies_review_id UNIQUE (review_id),
    CONSTRAINT fk_review_replies_review FOREIGN KEY (review_id)
        REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_replies_caretaker FOREIGN KEY (caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE
);
