# US6 — Role-aware access control

**Priority**: P3 · **Source**: [spec.md § User Story 6](../spec.md#user-scenarios--testing-mandatory)

## Story

An admin manages user accounts and role assignments. Every ticket action —
creating, transitioning, commenting, approving — is gated by the acting
user's role, enforced by the system, not just hidden in the interface.

**Why P3 (last, but not least-important)**: Roles are called out as core
business value in the constitution and doc 02 §2/§3, but as a cross-cutting
concern this story is exercised implicitly by Stories 1–5 — every acceptance
scenario in those stories already tests a role boundary. This story
specifically covers the admin-facing user/organization management surface
that the other stories don't touch.

## Acceptance scenarios

1. **Given** an admin creates a new user with role `DINIT_USER`, **when**
   that user logs in, **then** they can view and transition tickets
   assigned to Dinit but cannot approve proposals or manage other users.
2. **Given** a `CLIENT_USER`, **when** they attempt an admin-only action
   (e.g. creating another user), **then** the system rejects it regardless
   of whether a UI control was available to trigger it.
3. **Given** a `CLIENT_USER` belonging to Organization A, **when** they
   list tickets or view the dashboard, **then** they only see tickets
   belonging to Organization A — tickets from Organization B are neither
   listed nor directly accessible by ID.
4. **Given** a `DINIT_MANAGER`, **when** they view the dashboard, **then**
   counts and lists aggregate across all Organizations, not just one.

**Edge case** (see [spec.md § Edge Cases](../spec.md#edge-cases)): a Dinit
ticket lead assigned to a ticket from an Organization they haven't worked
with before is not restricted — Dinit staff are not partitioned by client.

## Requirements

FR-007 (server-side enforcement for every state-changing action), FR-008
(5-role minimum set), FR-015 (JWT authentication), FR-017/FR-018
(multi-tenant Organization isolation) — full text in
[spec.md § Functional Requirements](../spec.md#functional-requirements).

## Roles

`ADMIN`, `CLIENT_USER`, `CLIENT_APPROVER`, `DINIT_USER`, `DINIT_MANAGER` —
see [data-model.md § AppUser](../data-model.md#appuser) for the
org-required-for-client-roles rule, and [research.md § Authorization](../research.md#authorization-method-level-security--service-layer-transition-guard)
for how role checks are actually implemented (coarse `@PreAuthorize` +
fine-grained transition/org checks in the service layer — never UI-only).

## API

| Endpoint | Contract |
|---|---|
| `POST /api/auth/login` | [contracts/auth.md](../contracts/auth.md) |
| `GET /api/users/me` | [contracts/auth.md](../contracts/auth.md) |
| `GET /api/admin/users`, `POST /api/admin/users` | [contracts/admin.md](../contracts/admin.md) |
| `GET/POST/PATCH /api/admin/organizations` | [contracts/admin.md](../contracts/admin.md) |

Every other endpoint in the system also enforces role/org checks — see the
"Auth & organization scoping" section in [contracts/README.md](../contracts/README.md)
for the shared convention (cross-org access returns `404`, not `403`).

## Entities

`Organization`, `AppUser`.

## Tasks

- Phase 1 (Backend Foundation — dedicated to this story's admin surface):
  T011, T012
- Phase 7 (Frontend — admin users page): T064

Full task text: [tasks.md](../tasks.md). Like US4, most of this story's
actual enforcement work is threaded through every other phase (every
endpoint's role/org check), not concentrated in one phase — T011/T012/T064
are just the admin-management *screens*, not the whole story.

Verify gate: none dedicated — role enforcement is checked throughout (e.g.
T034 asserts a disallowed role gets `409`/`403`); org isolation is
specifically verified in T025 (Phase 2) and T069 (Phase 8, full
two-Organization check against SC-008).

## Success criteria

SC-005 (a `CLIENT_USER` can never successfully perform a Dinit-only or
approver-only action), SC-008 (Organization A never sees Organization B's
tickets, while a Dinit manager sees both) —
[spec.md § Success Criteria](../spec.md#success-criteria).
