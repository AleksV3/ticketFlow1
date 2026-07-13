# Feature Specification: TicketFlow1 Ticketing Tool — MVP

**Feature Branch**: `001-ticketing-mvp`

**Created**: 2026-07-02

**Last revised**: 2026-07-10 after Phase 3 review

**Status**: In progress — Phases 1–3 implemented; completion hardening pending

**Input**: User description: "A process-based internal ticketing tool for Change Management and Defect Management. It tracks Change Requests, Tasks, and Defects — each with its own validated lifecycle — requires client proposal approval for Change Requests, tracks defect severity and SLA deadlines, assigns current responsibility (CLIENT/TICKETFLOW1), and gives dashboards, comments, and an audit trail. Ticket types, their workflows, and roles are configuration each client company can adapt (seeded with sensible defaults), while access is enforced by permission server-side. Derived from docs/02-product-requirements-and-build-brief.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Change Request lifecycle with proposal approval (Priority: P1)

A client contributor submits a request for a service enhancement. A TicketFlow1 ticket
lead analyzes it and writes a proposal (scope, estimated delivery). A client
approver reviews the proposal and approves or rejects it. Once approved, TicketFlow1
develops, tests, and releases the change, and the ticket is closed.

**Why this priority**: This is the feature that makes the tool "process-based"
rather than generic CRUD. The proposal approval gate and the distinction
between Change Requests and Tasks is the single most business-specific
requirement in the brief (doc 02 §2). It runs on the seeded default **Change
Request** type and its default workflow.

**Independent Test**: Create a Change Request as a client user, move it through
Analysis, create a Proposal as a TicketFlow1 user, approve it as a user holding the
`PROPOSAL_APPROVE` permission on the client side, and progress it through
Development → First Occurrence Testing → User Acceptance Testing → Ready for
Production → In Production → Closed. Each step is independently verifiable by
checking the ticket's current status and that disallowed transitions are
rejected.

**Acceptance Scenarios**:

1. **Given** a client contributor is logged in, **When** they submit a new
   Change Request, **Then** the ticket is created with status `SUBMITTED`,
   type `CHANGE_REQUEST`, and `currentResponsibility = TICKETFLOW1`.
2. **Given** a Change Request in `SUBMITTED`, **When** a TicketFlow1 ticket lead
   moves it to `ANALYSIS` then creates a proposal, **Then** the proposal and
   ticket transition are committed together, the ticket status becomes
   `PROPOSAL`, and `currentResponsibility` switches to `CLIENT`.
3. **Given** a proposal in `PENDING`, **When** a user with the `PROPOSAL_APPROVE`
   permission on the client side approves it, **Then** the proposal status
   becomes `APPROVED`, the ticket status becomes `PROPOSAL_APPROVED` (then
   `DEVELOPMENT` once TicketFlow1 picks it up), and `currentResponsibility` switches
   back to `TICKETFLOW1`.
4. **Given** a proposal in `PENDING`, **When** the approver rejects it,
   **Then** the proposal status becomes `REJECTED`, the ticket status becomes
   `PROPOSAL_REJECTED`, and the ticket lead can move it back to `ANALYSIS` or
   to `CANCELLED`.
5. **Given** a Change Request not yet at `PROPOSAL_APPROVED`, **When** any user
   attempts to transition it directly to `DEVELOPMENT`, **Then** the system
   rejects the transition as invalid for the current state.
6. **Given** a client contributor whose role does not grant `PROPOSAL_APPROVE`,
   **When** they attempt to approve a proposal, **Then** the system rejects the
   action — approval requires the `PROPOSAL_APPROVE` permission and CLIENT party.
7. **Given** a user calls the generic ticket-transition endpoint, **When** they
   request `ANALYSIS → PROPOSAL` or a proposal approve/reject transition,
   **Then** the system rejects the operation — those transitions are available
   only as part of the corresponding proposal command.

---

### User Story 2 - Task lifecycle without proposal approval (Priority: P2)

A client or TicketFlow1 user submits a Task — an operational request that needs
action but no source-code change. It skips the proposal step entirely and
moves straight from analysis to execution to closure.

**Why this priority**: Demonstrates that ticket types are not forced into one
generic flow — each type carries its own workflow, and the seeded **Task** type
is simpler by design, not by omission (doc 02 §2, "Task lifecycle").

**Independent Test**: Create a Task, move it through `ANALYSIS` →
`DEVELOPMENT` → `FIRST_OCCURRENCE_TESTING` → `USER_ACCEPTANCE_TESTING` →
`READY_FOR_PRODUCTION` → `IN_PRODUCTION` → `CLOSED`, and verify no proposal
object is ever created or required.

**Acceptance Scenarios**:

1. **Given** a Task in `SUBMITTED`, **When** a TicketFlow1 user moves it to
   `ANALYSIS`, **Then** the next valid transition is directly to
   `DEVELOPMENT` — `PROPOSAL` is not a state in the Task type's workflow.
2. **Given** a Task, **When** any user attempts to create a Change Proposal
   against it, **Then** the system rejects the action — proposals apply only to
   types whose workflow contains a proposal step (the seeded Change Request type).

---

### User Story 3 - Defect reporting with severity and SLA tracking (Priority: P1)

A client user reports a defect and selects an initial severity
(`SEV_1`–`SEV_4`). The ticket lead validates or revises it during triage. The
system calculates response, first-info, and
next-update deadlines from that severity and continuously reflects whether the
ticket is `OK`, `DUE_SOON`, or `BREACHED`. Once fixed, the client confirms
resolution before the ticket closes.

**Why this priority**: SLA tracking on defects is doc 02's other standout
business-specific feature (§4) and directly reflects the mentor's Incident
Management SLA table — equal priority to the Change Request flow.

**Independent Test**: Create a `SEV_1` defect and verify `responseDueAt`,
`firstInfoDueAt`, and `slaStatus` are populated immediately per the SEV_1
formula; advance the clock (or backdate `createdAt` in a test) past the
response deadline and verify `slaStatus` flips to `BREACHED` without any
manual recalculation trigger.

**Acceptance Scenarios**:

1. **Given** a new Defect ticket, **When** it is created with severity
   `SEV_1`, **Then** `responseDueAt = createdAt + 15m`,
   `firstInfoDueAt = createdAt + 45m`, and `slaStatus = OK`.
2. **Given** a `SEV_1` defect whose `responseDueAt` has passed while
   `respondedAt` is absent, **When** the ticket or dashboard is viewed, **Then**
   `slaStatus` shows `BREACHED`.
3. **Given** a defect approaching (but not past) its next update deadline,
   **When** the ticket or dashboard is viewed, **Then** `slaStatus` shows
   `DUE_SOON`.
4. **Given** a defect ticket lead marks the fix deployed, **When** the client
   confirms resolution, **Then** the ticket moves to `CLOSED`; if the client
   does not confirm, the ticket stays in `CLIENT_CONFIRMATION`.
5. **Given** a `TASK` or `CHANGE_REQUEST` ticket, **When** its SLA fields are
   inspected, **Then** they are absent/`NOT_APPLICABLE` — SLA tracking applies
   only to `DEFECT` tickets. Severity is a fixed, non-configurable set because
   the SLA formulas are keyed to it.

---

### User Story 4 - Communication and audit trail in one place (Priority: P2)

Any ticket participant adds comments (internal notes visible only to TicketFlow1, or
public notes visible to the client) directly on a ticket. Every status change,
field change, comment, and proposal decision is recorded in an audit log and a
status-history timeline visible on the ticket.

**Why this priority**: Doc 02's stated purpose is to "store all documentation
and communication in one place" — this is what makes the tool a system of
record instead of a status tracker.

**Independent Test**: Add an internal comment as a TicketFlow1 user and confirm a
client user cannot see it; add a public comment and confirm the client user
can see it; perform a status transition and confirm an audit log entry and a
status-history entry both appear with correct actor and timestamp.

**Acceptance Scenarios**:

1. **Given** a user with `COMMENT_INTERNAL_WRITE` adds a comment marked
   `INTERNAL`, **When** a client user views the ticket, **Then** that comment is
   not visible to them (INTERNAL comments require a matching read permission
   that client-side default roles do not hold).
2. **Given** any transition, field change, or proposal decision on a ticket,
   **When** the ticket's history is viewed, **Then** every such event appears
   in the audit log with actor, action, old/new value, and timestamp.

---

### User Story 5 - Dashboard overview for prioritizing work (Priority: P2)

A TicketFlow1 manager or ticket lead opens a dashboard showing active vs. closed
ticket counts, tickets by type/status, defects by severity, SLA-breached and
due-soon defects, tickets waiting on client approval or confirmation, and
tickets assigned to them.

**Why this priority**: Doc 02 explicitly calls out overviews/dashboards as a
core purpose, and it is the fastest way to demonstrate the tool understands
the business process rather than just storing records.

**Independent Test**: With a mixed set of seeded tickets (various types,
statuses, severities, and SLA states), load the dashboard and verify each
card's count matches a direct query of the underlying ticket set.

**Acceptance Scenarios**:

1. **Given** at least one `BREACHED` defect exists, **When** the dashboard
   loads, **Then** it is listed under an "SLA breached" card.
2. **Given** tickets in `PROPOSAL` status, **When** the dashboard loads,
   **Then** they appear under "waiting for client approval".
3. **Given** a logged-in TicketFlow1 user, **When** they view "my assigned
   tickets", **Then** only tickets where they are the ticket lead appear.

---

### User Story 6 - Permission-based access control (Priority: P1)

Every ticket action — creating, transitioning, commenting, approving,
configuring — is gated by the acting user's **permissions**, enforced by the
system server-side, not just hidden in the interface. A user's permissions come
from the role assigned to them; the system never branches on a role's name.

**Why this priority**: Permission-based enforcement is the backbone that makes
configurable roles safe and makes every other story's access rules real rather
than cosmetic (constitution Principle III). Because it underlies Stories 1, 7,
and 8 it is P1, not a late add-on.

**Independent Test**: Assign a user a role granting only `TICKET_TRANSITION`;
confirm they can transition tickets but calling an approval or user-management
action is rejected server-side even when invoked directly.

**Acceptance Scenarios**:

1. **Given** a user whose role grants `TICKET_TRANSITION` but not
   `PROPOSAL_APPROVE` or `USER_MANAGE`, **When** they log in, **Then** they can
   transition tickets but cannot approve proposals or manage users.
2. **Given** a user without `USER_MANAGE`, **When** they attempt to create
   another user, **Then** the system rejects it regardless of whether a UI
   control was available to trigger it.
3. **Given** a client user belonging to Organization A, **When** they list
   tickets or view the dashboard, **Then** they only see tickets belonging to
   Organization A — tickets from Organization B are neither listed nor
   directly accessible by ID.
4. **Given** a TicketFlow1-side user (CLIENT/TICKETFLOW1 party = TICKETFLOW1), **When**
   they view the dashboard, **Then** counts and lists aggregate across all
   Organizations, not just one.

---

### User Story 7 - Configurable roles (Priority: P2)

An administrator composes a role as a bundle of permissions drawn from the
system's permission catalog, assigns it to users, and those users can perform
exactly the permitted actions. Renaming a role or creating a new one takes
effect without any code change.

**Why this priority**: Client companies differ in how they divide
responsibility; letting each define its own roles (rather than accept a fixed
five) is a core adaptability requirement. It is P2 because the seeded default
roles already deliver full process value on day one.

**Independent Test**: As an admin, create a role "Reviewer" granting
`TICKET_READ` + `COMMENT_PUBLIC_WRITE` only, assign it to a user, and confirm
that user can read tickets and post public comments but cannot transition,
approve, or see internal comments — verified by direct action attempts.

**Acceptance Scenarios**:

1. **Given** the permission catalog, **When** an admin creates a role from a
   chosen subset of permissions and assigns it to a user, **Then** that user's
   allowed actions match exactly the chosen permissions.
2. **Given** an existing role is renamed or has a permission added/removed by an
   admin, **When** an assigned user next authenticates or receives a newly
   issued token, **Then** their access reflects the change with no deployment.
3. **Given** any role configuration, **When** an admin attempts to grant a
   permission that is not in the fixed catalog, **Then** the system rejects it —
   the catalog is fixed; only role→permission mappings are editable.
4. **Given** a role on the client side, **When** it is configured, **Then** it
   can never grant TICKETFLOW1-party visibility — the CLIENT/TICKETFLOW1 party axis is
   structural and not something a role can change.

---

### User Story 8 - Configurable ticket types and workflows (Priority: P3)

An administrator defines a new ticket type and its workflow — the set of
statuses and the permitted transitions between them, each transition gated by a
required permission. Tickets of that type follow that workflow, and the system
rejects any transition the workflow does not define.

**Why this priority**: Adapting the *process* itself (not just roles) is the
deepest form of configurability. P3 because the three seeded default
types/workflows (Change Request, Task, Defect) already cover the brief's
scenarios; custom types are an extension companies grow into.

**Independent Test**: Define a type "Access Request" with states
`OPEN → GRANTED → CLOSED` (transitions gated by `TICKET_TRANSITION`); create a
ticket of that type; confirm `OPEN → CLOSED` is rejected (undefined) while
`OPEN → GRANTED` succeeds for a permitted user.

**Acceptance Scenarios**:

1. **Given** an admin has defined a type and its workflow, **When** a ticket of
   that type is created, **Then** it starts in the workflow's initial status and
   only the workflow's defined transitions are offered/accepted.
2. **Given** a configured workflow, **When** a transition not present in it is
   attempted, **Then** the system rejects it as invalid — identically to the
   seeded types (the validation guarantee is the same for default and custom
   workflows).
3. **Given** a transition whose required permission the actor lacks, **When**
   they attempt it, **Then** it is rejected on the permission, not silently
   allowed.

### Edge Cases

- What happens when a Change Request's proposal is rejected twice in a row —
  can it still be re-submitted for a third proposal, or must it be cancelled?
  (Default: no re-submission limit in MVP; ticket lead can always attempt
  another proposal or cancel.)
- What happens when a `SEV_1` defect's severity is later downgraded to
  `SEV_3` — do already-calculated SLA deadlines recompute from the original
  `createdAt` using the new severity's formula, or freeze at the original
  values? (Default: recompute using the new severity's formula from the
  original `createdAt`, and log the severity change in the audit trail.)
- How does the system handle a ticket transition attempted while the ticket
  is in a terminal status (e.g. `CLOSED`)? (Default: rejected — terminal
  statuses accept no further transitions unless the workflow explicitly defines
  a re-open transition, which the seeded defaults do not.)
- What happens if the only user holding `PROPOSAL_APPROVE` in an Organization
  is deactivated while a proposal is still `PENDING`? (Default: out of scope for
  MVP — user deactivation/deletion is not part of this feature; assume approver
  coverage is maintained.)
- How does the dashboard behave with zero tickets (fresh install)? (Default:
  all cards show zero/empty states, no errors.)
- What happens when a workflow is edited while tickets are mid-flow in a status
  that the edit removes? (Default: out of scope for MVP — workflow edits are
  assumed additive during a ticket's life; migration of in-flight tickets across
  breaking workflow changes is deferred past MVP.)
- What happens when two users transition or decide the same ticket/proposal at
  the same time? (Default: optimistic locking rejects the stale operation with
  `409 CONFLICT`; the caller reloads the latest state before retrying.)
- What happens when a TicketFlow1 ticket lead is assigned a ticket from an
  Organization they haven't worked with before? (Default: no restriction —
  TICKETFLOW1-party users are not partitioned by client.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support configurable **ticket types**, each carrying
  its own configurable status **workflow** (a set of statuses and the legal
  transitions between them). The system MUST be seeded with default types
  `CHANGE_REQUEST`, `TASK`, and `DEFECT`, each with a default workflow, so it is
  fully functional out of the box.
- **FR-002**: System MUST reject any status transition that the ticket type's
  configured workflow does not define, regardless of who requests it. The
  validation guarantee is identical for seeded and custom workflows: the engine
  enforces the workflow definition and never allows an undefined transition.
- **FR-003**: A ticket type's workflow MAY include a Change Proposal approval
  step; the seeded `CHANGE_REQUEST` workflow includes one, and a
  `CHANGE_REQUEST` MUST NOT proceed from `PROPOSAL` to `DEVELOPMENT` without an
  approved proposal. The seeded `TASK` and `DEFECT` workflows MUST NOT include a
  proposal step. Proposal-create/approve/reject transitions MUST be protected
  operations that cannot be invoked through the generic transition endpoint.
- **FR-004**: System MUST require a severity (`SEV_1`–`SEV_4`) on every
  `DEFECT` ticket and MUST calculate `responseDueAt`, `firstInfoDueAt`, and
  `nextUpdateDueAt` from that severity per the formulas in doc 02 §4. Severity
  is a **fixed** set (it drives the SLA formulas) and is not configurable.
- **FR-005**: System MUST expose a computed `slaStatus`
  (`OK`/`DUE_SOON`/`BREACHED`/`NOT_APPLICABLE`) for every `DEFECT` ticket,
  derived from current time, calculated deadlines, recorded response/info/update
  timestamps, and terminal state, without requiring a manual recalculation step.
- **FR-006**: System MUST track `currentResponsibility` (`CLIENT` or `TICKETFLOW1`)
  on every ticket and update it automatically on relevant status transitions
  (e.g. switches to `CLIENT` while a proposal is pending approval or a defect
  fix awaits confirmation).
- **FR-007**: System MUST enforce access by **permission**, checked server-side
  for every state-changing action (create, transition, comment, approve/reject
  proposal, manage users, configure roles/types/workflows). Authorization
  checks MUST test permissions and MUST NOT branch on role names. A disallowed
  action MUST be rejected even if invoked directly, not just hidden from the UI.
- **FR-008**: System MUST provide a **fixed permission catalog** defined in
  code (e.g. `TICKET_READ`, `TICKET_CREATE`, `TICKET_UPDATE`,
  `TICKET_TRANSITION`, `PROPOSAL_APPROVE`, `COMMENT_PUBLIC_WRITE`,
  `COMMENT_INTERNAL_READ`, `COMMENT_INTERNAL_WRITE`, `USER_MANAGE`, `ROLE_MANAGE`, `TYPE_MANAGE`,
  `WORKFLOW_MANAGE`). New
  permission keys are added only in code; they cannot be invented at runtime.
- **FR-009**: System MUST support **roles as configurable bundles of
  permissions**. It MUST be seeded with default role templates `ADMIN`,
  `CLIENT_USER`, `CLIENT_APPROVER`, `TICKETFLOW1_USER`, `TICKETFLOW1_MANAGER`. An admin
  MUST be able to create, rename, and re-scope roles by editing their permission
  sets, and such changes MUST take effect without a code deployment.
- **FR-010**: System MUST treat the **party axis** (`CLIENT` vs `TICKETFLOW1`) as a
  fixed structural attribute of every Organization and user. Party MUST NOT be
  configurable via roles, and no role may grant cross-party visibility. Party
  drives org-scoping, INTERNAL-comment visibility, and `currentResponsibility`.
- **FR-011**: System MUST gate approving/rejecting a Change Proposal on the
  `PROPOSAL_APPROVE` permission **and** CLIENT party. The seeded
  `CLIENT_APPROVER` role template holds `PROPOSAL_APPROVE`; an admin may grant it
  to other client-side roles.
- **FR-012**: System MUST allow comments on a ticket with a visibility of
  `INTERNAL` or `PUBLIC` (a fixed set). Reading or writing `INTERNAL` comments
  MUST require the corresponding permission, which client-side default roles do
  not hold.
- **FR-013**: System MUST record an audit entry (actor, action, target, old/new
  value, timestamp) for every status transition, field change (assignee,
  severity, responsibility), comment addition, proposal decision, and
  configuration change (organization/role/type/workflow edits). Ticket audit
  and configuration audit MAY use separate append-only stores; both MUST obey
  organization and visibility scoping.
- **FR-014**: System MUST record a status-history entry for every status
  transition on a ticket, viewable as a timeline on that ticket.
- **FR-015**: System MUST support filtering the ticket list by type, status,
  severity, assignee ("assigned to me"), current responsibility, SLA status,
  and free-text search.
- **FR-016**: System MUST provide a dashboard showing, at minimum: active vs.
  closed ticket counts, tickets by type, tickets by status, defects by
  severity, SLA-breached defects, SLA-due-soon defects, tickets waiting for
  client approval, tickets waiting for client confirmation, and the current
  user's assigned tickets.
- **FR-017**: System MUST record attachment references on a ticket as metadata
  (name, type, size, uploader). Storing or serving file bytes is outside MVP.
- **FR-018**: System MUST authenticate users before granting access to any
  ticket data or action, using a stateless token (JWT) issued at login and
  presented on subsequent requests via an `HttpOnly` cookie — no server-side
  session store. Browser state-changing requests MUST be protected against CSRF,
  and production authentication cookies MUST be `Secure`.
- **FR-019**: Every ticket MUST carry a priority field with values `LOW` /
  `MEDIUM` / `HIGH` / `CRITICAL` (a fixed set), settable by TICKETFLOW1-party users,
  shown on the ticket list/detail, and filterable. Priority is informational
  only — it does not drive SLA calculation or gate any status transition.
- **FR-020**: System MUST support multiple client Organizations with data
  isolation between them: a client-party user MUST only see tickets, comments,
  and dashboard data belonging to their own Organization. TICKETFLOW1-party users
  are not bound to a single Organization and MUST be able to see and act across
  all Organizations, since TicketFlow1 is the vendor serving all of them.
- **FR-021**: Every ticket MUST belong to exactly one Organization and remain in
  it for its lifetime. A CLIENT creator inherits their own Organization; a
  TICKETFLOW1 creator MUST explicitly choose the client Organization.
- **FR-022**: Configurable definitions (roles, ticket types, workflows) MUST be
  seeded from default **templates** and, for client-scoped definitions, cloned
  per Organization so each Organization can customize its own without affecting
  others. TICKETFLOW1-party roles are global to the vendor.
- **FR-023**: Concurrent mutations of a ticket, proposal, role, or workflow MUST
  not silently overwrite one another. A stale mutation MUST fail with a stable
  `409 CONFLICT` response.

### Key Entities

- **Organization**: A client company using the tool. Attributes: name,
  active flag, party (always CLIENT). Client-party users and the tickets their
  members create belong to exactly one Organization.
- **User**: An account with a party (`CLIENT`/`TICKETFLOW1`) and an assigned role.
  Attributes: name, email, party, role, active flag, Organization (required for
  client-party users, absent for TICKETFLOW1-party users).
- **Permission**: A fixed, code-defined action key (catalog per FR-008).
  Reference data seeded from code, not editable at runtime.
- **Role**: A named bundle of permissions. Seeded from default templates;
  client-scoped roles are cloned per Organization, TICKETFLOW1 roles are global.
  Editable by admins (which permissions it grants).
- **TicketType**: A configurable ticket category bound to a workflow. Seeded
  defaults: Change Request, Task, Defect.
- **Workflow / WorkflowState / WorkflowTransition**: The configured status set
  and legal transitions for a ticket type. Each transition records its required
  permission (and any responsibility/party effect). Seeded to reproduce the
  three default lifecycles.
- **Ticket**: The central record. Attributes: type, status, priority,
  severity (defects only), title/description, business owner, ticket lead,
  assigned team, Organization, current responsibility, created/updated/closed
  timestamps, SLA deadline fields and status (defects only).
- **ChangeProposal**: A scope/estimate proposal tied to a `CHANGE_REQUEST`
  ticket, with its own approve/reject decision, decided-by user, and decision
  timestamp. Exists only for tickets whose workflow includes a proposal step.
- **Comment**: A message on a ticket with an author, body, visibility
  (`INTERNAL`/`PUBLIC` — fixed), and timestamp.
- **Attachment**: A file (or file metadata) tied to a ticket, with uploader,
  filename, content type, and size.
- **AuditLog**: An immutable record of an action taken — actor, action type,
  field changed (if any), old/new value, timestamp.
- **StatusHistory**: A timeline entry recording a ticket's status at a point
  in time, distinct from the general audit log for quick lifecycle
  visualization.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Change Request can be taken from submission to closed through
  all lifecycle stages, including one proposal rejection and one
  resubmission, using only the tool's own actions (no manual data
  correction).
- **SC-002**: 100% of status transitions, field changes, comments, and
  proposal decisions performed during a demo session are visible in that
  ticket's audit trail immediately after the action.
- **SC-003**: For a newly created `SEV_1` defect, response and first-info
  deadlines are visible within the same page load that shows ticket creation
  confirmation, with no separate calculation step.
- **SC-004**: A TicketFlow1 manager can identify every SLA-breached and due-soon
  defect from a single dashboard view, without opening individual tickets.
- **SC-005**: A user whose role lacks a permission can never successfully
  perform the corresponding action (verified by attempting each disallowed
  action directly, not just checking the UI hides the button).
- **SC-006**: The 13-step demo story in doc 02 §12 can be performed start to
  finish by a presenter in under 10 minutes, on the seeded defaults, with no
  configuration required first.
- **SC-007**: A Task can be taken from submission to closed without a
  proposal ever being created or required, in fewer status transitions than
  the equivalent Change Request flow.
- **SC-008**: With two client Organizations seeded, a user from Organization
  A can never see, list, or open a ticket belonging to Organization B, while
  a TICKETFLOW1-party manager sees both in the same dashboard view.
- **SC-009**: An admin can create a new role from the permission catalog and a
  new ticket type with its own workflow, and both take effect for new
  tickets/users with no code change or restart.

## Assumptions

- The system ships with **seeded default** types, workflows, and roles that
  reproduce the brief's process exactly; a fresh install is immediately usable
  and every demo scenario runs on those defaults without configuration.
- `DISPUTE` ticket type is out of scope for MVP (doc 02 marks it optional); it
  can be added later as configuration — a new type + workflow — without
  restructuring.
- The permission catalog is fixed for MVP at the set in FR-008; adding a
  genuinely new *kind* of action is a code change, by design.
- Attachments are metadata-only for MVP (filename/type/size/uploader
  recorded; actual file bytes are not stored) per doc 02 §5's explicit
  allowance.
- SLA business-hours handling uses the simplified approximation in doc 02
  §4 ("MVP SLA implementation"), not a full business-calendar engine with
  holiday awareness.
- The system is multi-tenant: it hosts multiple client Organizations at once,
  each with its own Contributors and Approvers and its own cloned
  roles/types/workflows, isolated from each other. TICKETFLOW1-party users work
  across all Organizations (per FR-020/FR-021). Organization management
  (create/rename/deactivate) requires `USER_MANAGE`/admin; org self-service
  signup is out of scope for MVP.
- Workflow edits during MVP are assumed additive relative to in-flight tickets;
  deleting a state referenced by an existing ticket is rejected. Migrating
  tickets across breaking workflow changes is deferred past MVP.
- Demo users and fixed demo passwords exist only in a demo-specific migration
  profile/location and MUST NOT be applied in production environments.
- Ticket numbering/keys and pagination/page-size defaults are left to the
  implementation plan as standard patterns, not called out here since they
  don't materially change scope or user experience.
