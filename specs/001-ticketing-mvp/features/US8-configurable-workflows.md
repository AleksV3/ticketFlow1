# US8 — Configurable ticket types and workflows

**Priority**: P3 · **Source**: [spec.md § User Story 8](../spec.md#user-story-8---configurable-ticket-types-and-workflows-priority-p3)

## Story and boundaries

An authorized administrator creates an Organization-scoped ticket type and an
additive state/transition graph. Every custom transition uses
`operationKind=STANDARD`; proposal operation kinds remain owned by the seeded
Change Request protocol in MVP.

Workflow edits preserve existing state ids. A state referenced by a ticket
cannot be removed or renamed, and a stale concurrent edit fails with
`409 CONFLICT`.

## Acceptance focus

- Exactly one initial and at least one terminal state are required.
- Transitions reference states in the same workflow and fixed permission keys.
- CLIENT administrators cannot cross Organization boundaries.
- Generic transition validation is identical for seeded and custom workflows.
- Every mutation creates a configuration-audit entry.

## API, model, and tasks

- Contract: [admin.md](../contracts/admin.md#ticket-types--workflows)
- Model: [data-model.md](../data-model.md#workflow--workflowstate--workflowtransition)
- Tasks: T016–T017, T036, T038–T039, T072–T076, T089, T093, T100

Primary success criterion: SC-009 in [spec.md](../spec.md#success-criteria-mandatory).
