-- user_a_id/user_b_id cascade - a conversation referencing a now-deleted participant is
-- meaningless, same "owning relationship -> CASCADE" precedent used throughout this schema.
-- booking_id SETs NULL instead - an optional secondary reference (same precedent as
-- reviews.photo_file_asset_id), so a deleted booking just drops the conversation's booking
-- context rather than destroying the conversation and its message history.
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    booking_id BIGINT,
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_conversations_user_a FOREIGN KEY (user_a_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_user_b FOREIGN KEY (user_b_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE SET NULL
);

CREATE INDEX idx_conversations_user_a_id ON conversations (user_a_id);
CREATE INDEX idx_conversations_user_b_id ON conversations (user_b_id);
