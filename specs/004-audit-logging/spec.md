---
spec_id: CT-0004
status: proposed
dependencies:
  blocked_by: ['CT-0003']
---

# Durable audit trail

## Goal
Record security-relevant approvals and dispatch decisions.

## Acceptance criteria
- [ ] Every approval emits an actor-associated event
- [ ] Events are immutable via public APIs

## Non-goals
Unrelated changes and silent upstream API modifications.
