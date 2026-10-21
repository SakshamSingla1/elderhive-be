-- One profile per caretaker user (user_id unique), cascading if that user is ever deleted.
-- latitude/longitude/service_radius_km replace ElderSphere's free-text service_area so
-- Phase 11's proximity query can run a real haversine WHERE clause. specialties is a
-- separate element-collection table (caretaker_profile_specialties), not a column here -
-- a plain string placeholder until Phase 9's ServiceCategory table exists (Prompt 71).
CREATE TABLE caretaker_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bio TEXT,
    years_of_experience INT,
    hourly_rate NUMERIC(10, 2),
    rating_average DOUBLE PRECISION,
    verification_status VARCHAR(50) NOT NULL,
    profile_photo_file_asset_id BIGINT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    service_radius_km DOUBLE PRECISION,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_caretaker_profiles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_caretaker_profiles_user_id UNIQUE (user_id)
);

CREATE TABLE caretaker_profile_specialties (
    caretaker_profile_id BIGINT NOT NULL,
    specialty VARCHAR(255) NOT NULL,

    CONSTRAINT fk_caretaker_profile_specialties_profile FOREIGN KEY (caretaker_profile_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_caretaker_profile_specialties_profile_id ON caretaker_profile_specialties (caretaker_profile_id);
