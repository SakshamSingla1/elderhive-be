-- Converts caretaker_profile_specialties (Prompt 64) from a plain free-text element
-- collection (specialty VARCHAR) to a proper many-to-many join table against the now-real
-- service_categories table (Prompt 71). A straight ALTER/data-migration from arbitrary
-- caretaker-entered strings to real category ids isn't meaningful (there's no reliable
-- mapping from free text to a specific category row), so this drops and recreates the
-- table outright - acceptable at this stage of the project, with no production data to
-- preserve. service_category_id cascades on delete (unlike service_offerings.category_id's
-- RESTRICT, Prompt 72's other migration): losing a category just drops that tag from
-- caretaker profiles, not a primary record, so cascading is the reasonable behavior here.
DROP TABLE caretaker_profile_specialties;

CREATE TABLE caretaker_profile_specialties (
    caretaker_profile_id BIGINT NOT NULL,
    service_category_id BIGINT NOT NULL,

    CONSTRAINT pk_caretaker_profile_specialties PRIMARY KEY (caretaker_profile_id, service_category_id),
    CONSTRAINT fk_caretaker_profile_specialties_profile FOREIGN KEY (caretaker_profile_id)
        REFERENCES caretaker_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_caretaker_profile_specialties_category FOREIGN KEY (service_category_id)
        REFERENCES service_categories (id) ON DELETE CASCADE
);

CREATE INDEX idx_caretaker_profile_specialties_profile_id ON caretaker_profile_specialties (caretaker_profile_id);
