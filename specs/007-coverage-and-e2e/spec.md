---
spec_id: CT-0007
status: implemented
dependencies:
  blocked_by: ['CT-0002', 'CT-0004', 'CT-0006']
---

# 100 percent line coverage and E2E

## Goal
Achieve strict JaCoCo line coverage and verify browser behavior.

## Acceptance criteria
- [x] mvn clean verify passes 100% line coverage without exclusions
- [x] E2E tests exercise authorization and interview flows (`e2e/`, Playwright with real Keycloak logins; run by `scripts/run-e2e.sh` and the CI `e2e` job)

## Non-goals
Unrelated changes and silent upstream API modifications.
