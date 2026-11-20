-- A spec remembers the interview it was generated from. Specs created before this migration stay NULL.
ALTER TABLE specs ADD COLUMN interview_id VARCHAR(36);
