---
spec_id: CT-0001
status: proposed
dependencies:
  blocked_by: []
---

# Verify Git publication before dispatch

## Goal
Verify committed spec paths are present on the Cockpit-readable upstream branch.

Approval already pushes each spec to its own branch (`spec/<spec id>`) from an isolated container. This item covers checking that the branch has been merged into the branch OpenJCockpit reads, instead of relying on the operator's confirmation.

## Acceptance criteria
- [ ] Dispatch is blocked when the target ref lacks the spec
- [ ] The verified commit SHA is attached to the queue decision

## Non-goals
Unrelated changes and silent upstream API modifications.
