-- Interviews get creation/update timestamps; existing rows are backfilled with the migration time.
ALTER TABLE interviews ADD COLUMN created_at TIMESTAMP;
ALTER TABLE interviews ADD COLUMN updated_at TIMESTAMP;
UPDATE interviews SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;
-- Seeded templates are protected from edit and delete.
ALTER TABLE templates ADD COLUMN builtin BOOLEAN DEFAULT FALSE NOT NULL;
UPDATE templates SET builtin = TRUE WHERE name IN ('Project discovery', 'Feature discovery');
