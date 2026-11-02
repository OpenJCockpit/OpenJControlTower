-- Instance settings changed at runtime from the settings page; environment values are the defaults.
CREATE TABLE settings (
 name VARCHAR(64) PRIMARY KEY, value_json TEXT NOT NULL,
 updated_by VARCHAR(255), updated_at TIMESTAMP NOT NULL);
-- Who generated a spec, so policies can require a different person to approve it.
ALTER TABLE specs ADD COLUMN created_by VARCHAR(255);
