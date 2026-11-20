ALTER TABLE caretaker_profiles ADD COLUMN calendar_share_token VARCHAR(36);
CREATE UNIQUE INDEX idx_caretaker_profiles_calendar_share_token ON caretaker_profiles (calendar_share_token);
