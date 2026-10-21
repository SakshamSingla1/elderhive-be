-- family_user_id/elder_profile_id/caretaker_id cascade - a booking is meaningless once the
-- family user, the elder profile, or the caretaker profile it points at is gone (same
-- ownership-relationship CASCADE precedent as favorite_caretakers/caretaker_profiles).
-- service_offering_id has NO ON DELETE clause (RESTRICT/NO ACTION by default), matching
-- service_offerings' own FK to service_categories (V26) - booking history referencing a
-- catalog item must not silently vanish or get orphaned just because an admin later
-- deletes that offering; they'd have to deal with existing bookings first.
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    family_user_id BIGINT NOT NULL,
    elder_profile_id BIGINT NOT NULL,
    caretaker_id BIGINT NOT NULL,
    service_offering_id BIGINT NOT NULL,
    scheduled_date DATE,
    scheduled_time TIME,
    status VARCHAR(50) NOT NULL,
    cost NUMERIC(10, 2),
    notes TEXT,
    -- UUID string shared by every booking in a recurring series (e.g. a weekly booking
    -- created 4 at a time) - NULL for a one-off booking.
    recurring_group_id VARCHAR(36),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_bookings_family_user FOREIGN KEY (family_user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_elder_profile FOREIGN KEY (elder_profile_id)
        REFERENCES elder_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_caretaker FOREIGN KEY (caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_service_offering FOREIGN KEY (service_offering_id)
        REFERENCES service_offerings (id)
);

CREATE INDEX idx_bookings_family_user_id ON bookings (family_user_id);
CREATE INDEX idx_bookings_caretaker_id ON bookings (caretaker_id);
CREATE INDEX idx_bookings_status ON bookings (status);
-- Serves the slot-availability check: one caretaker's bookings at one date/time.
CREATE INDEX idx_bookings_caretaker_schedule ON bookings (caretaker_id, scheduled_date, scheduled_time);
CREATE INDEX idx_bookings_recurring_group_id ON bookings (recurring_group_id);
