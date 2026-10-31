---
spec_id: CT-0008
status: proposed
dependencies:
  blocked_by: ['CT-0002']
---

# Evaluate optional Embabel planning

## Goal
Introduce an optional Embabel adapter if workflow complexity justifies it.

## Acceptance criteria
- [ ] Behavior is equivalent when Embabel is disabled
- [ ] No agent can directly write files or dispatch without review

## Non-goals
Unrelated changes and silent upstream API modifications.
