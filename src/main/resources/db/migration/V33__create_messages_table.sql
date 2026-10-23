-- conversation_id/sender_id both cascade - a message referencing a now-deleted conversation
-- or sender is meaningless, same "owning relationship -> CASCADE" precedent used throughout
-- this schema (see V32's own conversations table for the identical reasoning).
-- file_asset_id SETs NULL instead - an optional secondary reference (same precedent as
-- reviews.photo_file_asset_id) - the resolved file_url column is left untouched, so the
-- message still shows a (now dead) attachment link rather than silently losing it.
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT,
    sent_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    file_asset_id BIGINT,
    file_url VARCHAR(500),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_file_asset FOREIGN KEY (file_asset_id)
        REFERENCES file_assets (id) ON DELETE SET NULL
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX idx_messages_sender_id ON messages (sender_id);
