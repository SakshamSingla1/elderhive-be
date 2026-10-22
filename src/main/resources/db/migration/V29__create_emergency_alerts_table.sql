-- elder_profile_id cascades, same reasoning as bookings.elder_profile_id (V28): alert
-- history pointing at a now-deleted elder profile is meaningless. triggered_by_user_id is
-- NOT NULL and cascades too, matching every other required user_id FK in this schema
-- (bookings.family_user_id, notifications.user_id, etc). responding_caretaker_id SETs
-- NULL instead - it's an optional secondary actor reference (same pattern as
-- elder_profiles.elder_user_id), so a deleted caretaker profile just drops the stale
-- responder rather than destroying the alert itself.
CREATE TABLE emergency_alerts (
    id BIGSERIAL PRIMARY KEY,
    elder_profile_id BIGINT NOT NULL,
    triggered_by_user_id BIGINT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address TEXT,
    status VARCHAR(50) NOT NULL,
    responding_caretaker_id BIGINT,
    response_time_seconds INTEGER,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_emergency_alerts_elder_profile FOREIGN KEY (elder_profile_id)
        REFERENCES elder_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_emergency_alerts_triggered_by_user FOREIGN KEY (triggered_by_user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_emergency_alerts_responding_caretaker FOREIGN KEY (responding_caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE SET NULL
);

CREATE INDEX idx_emergency_alerts_elder_profile_id ON emergency_alerts (elder_profile_id);
CREATE INDEX idx_emergency_alerts_status ON emergency_alerts (status);
CREATE INDEX idx_emergency_alerts_responding_caretaker_id ON emergency_alerts (responding_caretaker_id);
