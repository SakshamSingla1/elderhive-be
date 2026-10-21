-- storage_key is the opaque identifier FileStorageService needs to locate/delete the file
-- again (a local-disk relative path, or "cloudinary:<resourceType>:<publicId>") - no url
-- column, it's resolved on demand. uploaded_by_user_id has no FK (mirrors the project's
-- general pattern of not FK-constraining an audit-ish reference column).
CREATE TABLE file_assets (
    id BIGSERIAL PRIMARY KEY,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(255),
    size BIGINT,
    uploaded_by_user_id BIGINT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT
);

-- caretaker_id FKs to caretaker_profiles.id, cascading if that profile is deleted.
-- file_asset_id FKs to file_assets.id, also cascading - a verification document row is
-- meaningless once its underlying file is gone.
CREATE TABLE caretaker_verification_documents (
    id BIGSERIAL PRIMARY KEY,
    caretaker_id BIGINT NOT NULL,
    file_asset_id BIGINT NOT NULL,
    doc_type VARCHAR(255),
    review_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_caretaker_verification_documents_caretaker FOREIGN KEY (caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_caretaker_verification_documents_file_asset FOREIGN KEY (file_asset_id)
        REFERENCES file_assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_caretaker_verification_documents_caretaker_id ON caretaker_verification_documents (caretaker_id);
