# Phase 0 Research: TicketFlow1 Ticketing Tool — MVP

**Last revised**: 2026-07-10 after Phase 3 architecture/security review

All technology choices are fixed by the [constitution](../../.specify/memory/constitution.md)
(Java 21/Spring Boot/PostgreSQL backend, Next.js/TypeScript/Tailwind frontend).
This document resolves the *how*, not the *what* — the specific patterns and
libraries used to implement the constitution's fixed stack correctly.

## Authentication: stateless JWT in `HttpOnly` cookie

**Decision**: Spring Security with a custom `OncePerRequestFilter` that
validates a JWT on every request, backed by `io.jsonwebtoken` (jjwt) for
signing/parsing. The JWT is issued at login and delivered to the browser in an
`HttpOnly` cookie rather than exposed to frontend JavaScript. Passwords hashed
with BCrypt (`BCryptPasswordEncoder`). The JWT carries the user's `party`,
`organizationId`, and the set of **permission** authorities resolved from its
role. No refresh tokens for MVP — access token expiry set generously (e.g. 8
hours) since this is an internal demo tool.

**Rationale**: Resolved in spec clarification (FR-018). JWT needs no
server-side session store while avoiding token exposure through
`localStorage`/`sessionStorage`. Cookie delivery still lets the demo/dev
workflow log in as multiple roles in parallel browser profiles/Postman without
building a server-side session store. Standard, well-documented Spring
Security integration path — good learning value for understanding filter
chains.

**Alternatives considered**: Server-side session + cookie — rejected because it
adds server-side session state we do not need for the MVP, while the same UX
goal is satisfied by a stateless JWT carried in a cookie. Exposing the JWT to
frontend JavaScript via `localStorage` or an in-memory bearer-token pattern was
rejected because the token should not be script-readable when a secure
`HttpOnly` delivery path is available.

**Security details**: Browser mutations use a CSRF token cookie plus matching
request header; CORS uses an explicit environment-configured origin allowlist.
The auth cookie is `Secure` outside the local-development profile. Permissions
embedded in an eight-hour JWT are a snapshot: role changes take effect on the
user's next login/token issuance, not their next request. Immediate revocation
is deferred beyond MVP; deactivating a user prevents new login and existing
tokens expire naturally.

## Authorization: permission authorities + config-driven transition engine

**Decision**: Access is enforced by **permission**, never by role name.
Coarse-grained checks use `@PreAuthorize("hasAuthority('<PERMISSION_KEY>')")`
at the controller/service boundary (e.g. `USER_MANAGE` for user-management
endpoints). Fine-grained checks — "may *this* actor make *this* transition on
*this* ticket" — live in a dedicated config-driven `TicketTransitionService`
that loads the ticket type's `WorkflowTransition` rows out of the current
state and accepts a move only if one exists whose `required_permission` the
actor holds and whose `required_party` (when set) matches, throwing a typed
`IllegalTransitionException` (HTTP 409) otherwise. Organization scoping
(FR-020/FR-021) is enforced by an org filter at the repository/query layer for
CLIENT-party callers, never trusted from client-supplied parameters.

Workflow transitions carry an `operation_kind`. The public generic transition
command accepts only `STANDARD`; proposal create/approve/reject kinds are
invoked only by `ChangeProposalService`. This keeps configurable state machines
without allowing a permission-bearing caller to skip the required proposal
record or decision. Mutable aggregate roots use optimistic version columns and
return `409 CONFLICT` when a stale concurrent command loses the race.

**Rationale**: Checking permissions (not role names) is what lets an admin
create or rename a role without a code change (FR-009). Loading transition
legality from workflow data keeps the rules in one auditable place and lets
each Organization configure its own workflows — directly serving constitution
Principles I (validated transitions) and III (permission-based access).

**Alternatives considered**: A generic state-machine library (e.g. Spring
Statemachine) — rejected as overengineering (Principle VI); a data-driven
lookup + service is easier to read, test, and explain. Branching on role
names in `@PreAuthorize` — rejected because it makes roles un-configurable.

## Ticket types & workflows: configuration stored as data

**Decision**: Ticket **types** and their **workflows** (states + transitions)
are lookup tables (`ticket_type`, `workflow`, `workflow_state`,
`workflow_transition`), seeded with the three default types (Change Request,
Task, Defect) and cloned per Organization so each can customize its own. A
ticket references its `ticket_type_id` and `current_state_id`; the transition
engine reads the workflow's rows to decide legality. Each transition row
carries its `required_permission`, an optional `required_party`, and a
`responsibility_after` effect.

**Rationale**: Storing workflows as data is what makes "Task skips the proposal
step, Change Request doesn't" a property of configuration rather than code —
and lets a company add a new type/workflow at runtime (spec US8). The seeded
defaults reproduce the brief's exact lifecycles, so the app is fully
functional out of the box.

**Alternatives considered**: Hard-coding each type's transition map as static
data in Java — rejected because it cannot express per-Organization workflows
and would put the process rules where only a developer can change them. A
free-form scripting/rules engine — rejected as overengineering (Principle VI);
states + guarded transitions are enough.

## Identifiers & value-set representation

**Decision**: Primary keys are `bigint GENERATED ALWAYS AS IDENTITY`.
**Fixed** value sets (party, severity, priority, comment visibility, proposal
status, responsibility, audit action) are `TEXT` columns with `CHECK`
constraints; **configurable** sets (ticket type, workflow state, role) are
lookup tables.

**Rationale**: An internal single-Postgres tool gains nothing from distributed
id generation, and access is gated by the authorization/org layer rather than
by ids being unguessable — so sequential `bigint` keys are simpler and cheaper.
A `CHECK` constraint gives the same integrity guarantee as a native type for a
fixed set while evolving with one `ALTER TABLE`; configurable sets need lookup
tables regardless. Severity stays fixed because the SLA formulas are keyed to
`SEV_1`–`SEV_4`.

**Alternatives considered**: UUID primary keys — rejected as overkill for this
scale and deployment (no distributed inserts; opacity is not the access
control). Native `ENUM` types for the fixed sets — not used, because a single
`CHECK` evolves a fixed set more simply and the configurable sets are lookup
tables anyway.

## SLA calculation: event-aware and computed at read time

**Decision**: `responseDueAt`, `firstInfoDueAt`, and the initial
`nextUpdateDueAt` are persisted when the required creation-time severity is
set (and recalculated if severity changes). `respondedAt` is set on the first
`REPORTED → ANALYSIS` transition; `firstInfoAt` is set on the first PUBLIC
comment authored by a TICKETFLOW1 user; that comment and each later qualifying
PUBLIC update advance `nextUpdateDueAt` for SEV_1/SEV_2. `slaStatus`
(`OK`/`DUE_SOON`/`BREACHED`/`NOT_APPLICABLE`) is NOT persisted. It is computed
from unmet deadlines:

- `NOT_APPLICABLE`: non-Defect or terminal ticket.
- `BREACHED`: at least one applicable deadline is past and its completion
  timestamp is absent.
- `DUE_SOON`: no breach, but an unmet deadline is inside the final 25% of its
  original SLA duration (minimum warning window five minutes).
- `OK`: otherwise.

For SEV_3/SEV_4, "next business day" means 08:00 in `Europe/Ljubljana` on the
next Monday–Friday; public holidays are intentionally ignored for MVP.

**Rationale**: A persisted `slaStatus` would require a scheduled job ticking
every N minutes to flip it, adding infra complexity (Principle VI) for a value
cheap to compute on read and always consistent. Doc 02 explicitly asks for a
"useful approximation," not a business-calendar engine.

**Alternatives considered**: A `@Scheduled` job recalculating and persisting
`slaStatus` — rejected for MVP; adds a moving part with no correctness benefit.
The dashboard and ticket-list filter query the due/completion columns using the
same predicates as `SlaStatusService`, so pagination and detail responses cannot
disagree.

## Audit stores: ticket events vs. configuration events

**Decision**: `audit_log` remains ticket-scoped. A separate append-only
`configuration_audit_log` records organization, role, ticket-type, and workflow
mutations with `organization_id`, target type/id, actor, action, and old/new
JSON summaries. This avoids fake/null ticket references and gives admin changes
correct tenant scoping. Ticket audit entries for INTERNAL comments never store
the body and are hidden from callers who cannot see the source comment.

## Testing strategy

**Decision**: JUnit 5 + Spring Boot Test for the backend, with the transition
engine and SLA-calculation logic covered by fast unit tests (no Spring
context), and Testcontainers-backed `@SpringBootTest` integration tests for the
repository/controller layers against a real PostgreSQL container (not H2).
Frontend coverage stays deliberately narrow: component tests for auth/API and
permission-driven rendering, one end-to-end smoke flow, and manual
accessibility/responsive verification.

**Rationale**: Doc 02 §8 lists Testcontainers as optional; making it the
integration-test backend (rather than H2) costs little extra setup and teaches
a pattern used in real Spring shops. Unit-testing the transition engine and SLA
math without a Spring context keeps the feedback loop fast (Principle V).

**Alternatives considered**: H2 in-memory DB for integration tests — rejected:
PostgreSQL-specific behavior (`CHECK` constraints, lookup FKs, Flyway-managed
schema) drifts enough from H2 to produce false confidence.

## Frontend data fetching

**Decision**: Plain `fetch` wrapped in a small typed API client
(`lib/api.ts`), no TanStack Query for MVP. Authenticated MVP pages use client
fetches with `credentials: 'include'`; a Server Component does not automatically
forward the browser's HttpOnly cookie to the separately hosted Spring API.
Public layout/static content may remain Server Components. A same-origin Next.js
backend-for-frontend can be introduced later if server rendering becomes a
real requirement.

**Rationale**: Doc 02 marks TanStack Query optional. Given the 30-day timeline
and that backend workflow logic is the priority (Principle IV), a client-state
library is complexity to defer. It can be added later without restructuring if
the manual `fetch` + `useState` pattern becomes painful.

**Alternatives considered**: TanStack Query from day one — rejected for now
(Principle VI); revisit only if manual cache invalidation across
list/detail/dashboard becomes a real pain point in Phase 7.
