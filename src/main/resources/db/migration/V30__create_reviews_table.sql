-- booking_id/reviewer_user_id/caretaker_id all cascade - a review referencing a
-- now-deleted booking, reviewing user, or caretaker profile is meaningless, same
-- "owning relationship -> CASCADE" precedent as bookings' own three required FKs (V28).
-- photo_file_asset_id SETs NULL instead - an optional secondary reference (same
-- precedent as elder_profiles.elder_user_id, emergency_alerts.responding_caretaker_id),
-- so a deleted file asset just drops the stale photo rather than destroying the review.
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    caretaker_id BIGINT NOT NULL,
    overall_rating INTEGER NOT NULL,
    comment TEXT,
    punctuality_rating INTEGER,
    care_quality_rating INTEGER,
    communication_rating INTEGER,
    photo_file_asset_id BIGINT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT uk_reviews_booking_id UNIQUE (booking_id),
    CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer_user FOREIGN KEY (reviewer_user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_caretaker FOREIGN KEY (caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_photo_file_asset FOREIGN KEY (photo_file_asset_id)
        REFERENCES file_assets (id) ON DELETE SET NULL
);

CREATE INDEX idx_reviews_caretaker_id ON reviews (caretaker_id);
CREATE INDEX idx_reviews_reviewer_user_id ON reviews (reviewer_user_id);
