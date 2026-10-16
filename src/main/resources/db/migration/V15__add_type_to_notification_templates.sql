-- notification_templates has no rows yet anywhere (Prompt 59 is the first to seed real
-- template rows) so this NOT NULL column needs no default/backfill step.
ALTER TABLE notification_templates ADD COLUMN type VARCHAR(50) NOT NULL;
