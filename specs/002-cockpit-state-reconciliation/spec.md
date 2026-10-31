---
spec_id: CT-0002
status: proposed
dependencies:
  blocked_by: ['CT-0001']
---

# Reconcile OpenJCockpit queue state

## Goal
Replace manual DONE assertions with verified upstream status.

## Acceptance criteria
- [ ] No dependent spec is READY until predecessor succeeded
- [ ] Uncertain dispatch outcomes are reconciled by a read

## Non-goals
Unrelated changes and silent upstream API modifications.
