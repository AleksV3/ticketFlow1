# US7 — Configurable roles

**Priority**: P2 · **Source**: [spec.md § User Story 7](../spec.md#user-story-7---configurable-roles-priority-p2)

## Story and boundaries

An authorized administrator creates or edits a role as a bundle of keys from
the fixed permission catalog. CLIENT roles belong to exactly one Organization;
TICKETFLOW1 roles are global. Role names never appear in authorization code.

Permissions embedded in a JWT are a snapshot: an edit takes effect for assigned
users on their next login/token issuance. Immediate revocation of existing
tokens is outside MVP.

## Acceptance focus

- Unknown permission keys are rejected.
- CLIENT administrators cannot read or mutate another Organization's roles.
- Party and Organization are immutable after role creation.
- Concurrent edits do not silently overwrite each other.
- Every mutation creates an organization-scoped configuration-audit entry.

## API, model, and tasks

- Contract: [admin.md](../contracts/admin.md#roles--permissions)
- Model: [data-model.md](../data-model.md#role) and
  [ConfigurationAuditLog](../data-model.md#configurationauditlog)
- Tasks: T014, T036, T039, T073, T075–T076, T088, T093, T100

Primary success criterion: SC-005/SC-009 in [spec.md](../spec.md#success-criteria-mandatory).
