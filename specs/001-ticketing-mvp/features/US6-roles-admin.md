# US6 — Permission-based access control

**Priority**: P1 · **Source**: [spec.md § User Story 6](../spec.md#user-scenarios--testing-mandatory)

## Story

An admin manages user accounts and role assignments. Every ticket action —
creating, transitioning, commenting, approving, configuring — is gated by the
acting user's **permissions**, enforced by the system server-side, not just
hidden in the interface. A user's permissions come from the role assigned to
them; the system never branches on a role's name.

**Why P1**: Permission-based enforcement is the backbone that makes
configurable roles safe and makes every other story's access rules real
rather than cosmetic (constitution Principle III). It is exercised implicitly
by Stories 1–5 — every acceptance scenario in those stories tests a
permission boundary — and this page additionally covers the admin-facing
user/organization management surface that the other stories don't touch.

## Acceptance scenarios

1. **Given** a user whose role grants `TICKET_TRANSITION` but not
   `PROPOSAL_APPROVE` or `USER_MANAGE`, **when** they log in, **then** they can
   transition tickets but cannot approve proposals or manage users.
2. **Given** a user without `USER_MANAGE`, **when** they attempt to create
   another user, **then** the system rejects it regardless of whether a UI
   control was available to trigger it.
3. **Given** a client user belonging to Organization A, **when** they list
   tickets or view the dashboard, **then** they only see tickets belonging to
   Organization A — tickets from Organization B are neither listed nor
   directly accessible by ID.
4. **Given** a TicketFlow1-side user (party = TICKETFLOW1), **when** they view the
   dashboard, **then** counts and lists aggregate across all Organizations,
   not just one.

**Edge case** (see [spec.md § Edge Cases](../spec.md#edge-cases)): a
TicketFlow1 ticket lead assigned to a ticket from an Organization they haven't
worked with before is not restricted — TICKETFLOW1-party users are not
partitioned by client.

## Permissions & seeded roles

Access is enforced by **permission**, not by role name. The permission
catalog is fixed in code (e.g. `TICKET_READ`, `TICKET_CREATE`,
`TICKET_UPDATE`, `TICKET_TRANSITION`, `PROPOSAL_APPROVE`, `COMMENT_PUBLIC_WRITE`,
`COMMENT_INTERNAL_READ`, `COMMENT_INTERNAL_WRITE`, `USER_MANAGE`, `ROLE_MANAGE`, `TYPE_MANAGE`,
`WORKFLOW_MANAGE`). Roles are configurable **bundles of permissions**, seeded
from default templates: `ADMIN`, `CLIENT_USER`, `CLIENT_APPROVER`,
`TICKETFLOW1_USER`, `TICKETFLOW1_MANAGER`. The **party axis** (`CLIENT` vs
`TICKETFLOW1`) is a fixed structural attribute of every user; Organizations
represent CLIENT tenants;
no role can grant cross-party visibility. See
[data-model.md § AppUser](../data-model.md#appuser) for the
org-required-for-client-side-users rule, and
[research.md § Authorization](../research.md#authorization-permission-authorities--config-driven-transition-engine)
for how permission checks are implemented (coarse `@PreAuthorize` on
permission authorities + fine-grained transition/org checks in the service
layer — never UI-only).

## Requirements

FR-007 (server-side permission enforcement for every state-changing action),
FR-008 (fixed permission catalog), FR-009 (roles as configurable permission
bundles, seeded templates), FR-018 (JWT authentication), FR-020/FR-021
(multi-tenant Organization isolation) — full text in
[spec.md § Functional Requirements](../spec.md#functional-requirements).

## API

| Endpoint | Contract |
|---|---|
| `POST /api/auth/login` | [contracts/auth.md](../contracts/auth.md) |
| `GET /api/users/me` | [contracts/auth.md](../contracts/auth.md) |
| `GET /api/admin/users`, `POST /api/admin/users` | [contracts/admin.md](../contracts/admin.md) |
| `GET/POST/PATCH /api/admin/organizations` | [contracts/admin.md](../contracts/admin.md) |

Every other endpoint in the system also enforces permission/org checks — see
the "Auth & organization scoping" section in [contracts/README.md](../contracts/README.md)
for the shared convention (cross-org access returns `404`, not `403`).

## Entities

`Organization`, `AppUser`, `Role`, `Permission`.

## Tasks

- Phase 1 (Backend Foundation — dedicated to this story's admin surface):
  T012, T013
- Phase 3 hardening: T039–T041
- Phase 7 (admin scope and UI): T073–T079, T087, T093

Full task text: [tasks.md](../tasks.md). Like US4, most of this story's
actual enforcement work is threaded through every other phase (every
endpoint's permission/org check), not concentrated in one phase —
T012/T013/T087 are just the concentrated admin-management tasks, not the whole story.

Verify gate: none dedicated — permission enforcement is checked throughout
(e.g. T034 asserts an actor lacking the required permission gets `409`/`403`);
org isolation is specifically verified in T027 (Phase 2) and T100 (Phase 8,
full two-Organization check against SC-008).

## Success criteria

SC-005 (a user whose role lacks a permission can never successfully perform
the corresponding action), SC-008 (Organization A never sees Organization B's
tickets, while a TicketFlow1 manager sees both) —
[spec.md § Success Criteria](../spec.md#success-criteria).
