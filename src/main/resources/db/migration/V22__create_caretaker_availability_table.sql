-- One row per weekly recurring availability slot. Cascades with its caretaker profile -
-- a slot is meaningless once the profile it belongs to is gone. caretaker_id is explicitly
-- indexed - every query here (list, ownership checks) filters by it.
CREATE TABLE caretaker_availability (
    id BIGSERIAL PRIMARY KEY,
    caretaker_id BIGINT NOT NULL,
    day_of_week VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_caretaker_availability_caretaker FOREIGN KEY (caretaker_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_caretaker_availability_caretaker_id ON caretaker_availability (caretaker_id);
