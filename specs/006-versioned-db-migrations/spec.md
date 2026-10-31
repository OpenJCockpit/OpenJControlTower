---
spec_id: CT-0006
status: implemented
dependencies:
  blocked_by: []
---

# Versioned database migrations

## Goal
Replace schema.sql with automated versioned migrations.

## Acceptance criteria
- [x] Fresh and upgrade migrations are tested (Flyway in `DataStoreTest` and `ControlTowerIntegrationTest`; existing `schema.sql` databases are adopted via `baseline-on-migrate`)
- [x] Restore procedure is documented (`docs/KUBERNETES.md`, Backups)

## Non-goals
Unrelated changes and silent upstream API modifications.
