-- Projects point to a remote Git repository with credentials instead of a local working copy.
-- Existing projects keep their row but need a Git URL before their next spec approval.
ALTER TABLE projects ADD COLUMN git_url VARCHAR(500);
ALTER TABLE projects ADD COLUMN default_branch VARCHAR(200) DEFAULT 'main' NOT NULL;
ALTER TABLE projects ADD COLUMN credential_type VARCHAR(32) DEFAULT 'NONE' NOT NULL;
ALTER TABLE projects ADD COLUMN git_username VARCHAR(255);
-- AES-256-GCM ciphertext (Spring Security Encryptors.delux); never returned by the API.
ALTER TABLE projects ADD COLUMN encrypted_secret TEXT;
ALTER TABLE projects DROP COLUMN root_path;
ALTER TABLE specs ADD COLUMN branch VARCHAR(255);
ALTER TABLE specs ADD COLUMN commit_sha VARCHAR(64);
