# Feature Specification: Dinit Ticketing Tool — MVP

**Feature Branch**: `001-ticketing-mvp`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "A process-based internal ticketing tool for Change Management and Defect Management: tracks Change Requests, Tasks, and Defects (each with its own validated lifecycle), requires client proposal approval for Change Requests, tracks defect severity and SLA deadlines, assigns current responsibility (CLIENT/DINIT), enforces role-based actions, and gives dashboards, comments, and an audit trail. Derived from docs/02-product-requirements-and-build-brief.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Change Request lifecycle with proposal approval (Priority: P1)

A client contributor submits a request for a service enhancement. A Dinit ticket
lead analyzes it and writes a proposal (scope, estimated delivery). A client
approver reviews the proposal and approves or rejects it. Once approved, Dinit
develops, tests, and releases the change, and the ticket is closed.

**Why this priority**: This is the feature that makes the tool "process-based"
rather than generic CRUD. The proposal approval gate and the distinction
between Change Requests and Tasks is the single most business-specific
requirement in the brief (doc 02 §2).

**Independent Test**: Create a Change Request as a client user, move it through
Analysis, create a Proposal as a Dinit user, approve it as a client approver,
and progress it through Development → First Occurrence Testing → User
Acceptance Testing → Ready for Production → In Production → Closed. Each step
is independently verifiable by checking the ticket's current status and that
disallowed transitions are rejected.

**Acceptance Scenarios**:

1. **Given** a client contributor is logged in, **When** they submit a new
   Change Request, **Then** the ticket is created with status `SUBMITTED`,
   type `CHANGE_REQUEST`, and `currentResponsibility = DINIT`.
2. **Given** a Change Request in `SUBMITTED`, **When** a Dinit ticket lead
   moves it to `ANALYSIS` then creates a proposal, **Then** the ticket status
   becomes `PROPOSAL` and `currentResponsibility` switches to `CLIENT`.
3. **Given** a proposal in `PENDING`, **When** the client approver approves it,
   **Then** the proposal status becomes `APPROVED`, the ticket status becomes
   `PROPOSAL_APPROVED` (then `DEVELOPMENT` once Dinit picks it up), and
   `currentResponsibility` switches back to `DINIT`.
4. **Given** a proposal in `PENDING`, **When** the client approver rejects it,
   **Then** the proposal status becomes `REJECTED`, the ticket status becomes
   `PROPOSAL_REJECTED`, and the ticket lead can move it back to `ANALYSIS` or
   to `CANCELLED`.
5. **Given** a Change Request not yet at `PROPOSAL_APPROVED`, **When** any user
   attempts to transition it directly to `DEVELOPMENT`, **Then** the system
   rejects the transition as invalid for the current state.
6. **Given** a client contributor, **When** they attempt to approve their own
   proposal, **Then** the system rejects the action — only `CLIENT_APPROVER`
   role may approve/reject.

---

### User Story 2 - Task lifecycle without proposal approval (Priority: P2)

A client or Dinit user submits a Task — an operational request that needs
action but no source-code change. It skips the proposal step entirely and
moves straight from analysis to execution to closure.

**Why this priority**: Demonstrates that ticket types are not forced into one
generic flow — a Task is simpler by design, not by omission (doc 02 §2, "Task
lifecycle").

**Independent Test**: Create a Task, move it through `ANALYSIS` →
`DEVELOPMENT` → `FIRST_OCCURRENCE_TESTING` → `USER_ACCEPTANCE_TESTING` →
`READY_FOR_PRODUCTION` → `IN_PRODUCTION` → `CLOSED`, and verify no proposal
object is ever created or required.

**Acceptance Scenarios**:

1. **Given** a Task in `SUBMITTED`, **When** a Dinit user moves it to
   `ANALYSIS`, **Then** the next valid transition is directly to
   `DEVELOPMENT` — `PROPOSAL` is not a legal state for this ticket type.
2. **Given** a Task, **When** any user attempts to create a Change Proposal
   against it, **Then** the system rejects the action — proposals only apply
   to `CHANGE_REQUEST` tickets.

---

### User Story 3 - Defect reporting with severity and SLA tracking (Priority: P1)

A client user reports a defect. The ticket lead triages it and assigns a
severity (`SEV_1`–`SEV_4`). The system calculates response, first-info, and
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

1. **Given** a new Defect ticket, **When** a ticket lead sets severity to
   `SEV_1`, **Then** `responseDueAt = createdAt + 15m`,
   `firstInfoDueAt = createdAt + 45m`, and `slaStatus = OK`.
2. **Given** a `SEV_1` defect whose `responseDueAt` has passed with no
   response recorded, **When** the ticket or dashboard is viewed, **Then**
   `slaStatus` shows `BREACHED`.
3. **Given** a defect approaching (but not past) its next update deadline,
   **When** the ticket or dashboard is viewed, **Then** `slaStatus` shows
   `DUE_SOON`.
4. **Given** a defect ticket lead marks the fix deployed, **When** the client
   confirms resolution, **Then** the ticket moves to `CLOSED`; if the client
   does not confirm, the ticket stays in `CLIENT_CONFIRMATION`.
5. **Given** a `TASK` or `CHANGE_REQUEST` ticket, **When** its SLA fields are
   inspected, **Then** they are absent/`NOT_APPLICABLE` — SLA tracking only
   applies to `DEFECT` tickets.

---

### User Story 4 - Communication and audit trail in one place (Priority: P2)

Any ticket participant adds comments (internal notes visible only to Dinit, or
public notes visible to the client) directly on a ticket. Every status change,
field change, comment, and proposal decision is recorded in an audit log and a
status-history timeline visible on the ticket.

**Why this priority**: Doc 02's stated purpose is to "store all documentation
and communication in one place" — this is what makes the tool a system of
record instead of a status tracker.

**Independent Test**: Add an internal comment as a Dinit user and confirm a
client user cannot see it; add a public comment and confirm the client user
can see it; perform a status transition and confirm an audit log entry and a
status-history entry both appear with correct actor and timestamp.

**Acceptance Scenarios**:

1. **Given** a Dinit user adds a comment marked `INTERNAL`, **When** a client
   user views the ticket, **Then** that comment is not visible to them.
2. **Given** any transition, field change, or proposal decision on a ticket,
   **When** the ticket's history is viewed, **Then** every such event appears
   in the audit log with actor, action, old/new value, and timestamp.

---

### User Story 5 - Dashboard overview for prioritizing work (Priority: P2)

A Dinit manager or ticket lead opens a dashboard showing active vs. closed
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
3. **Given** a logged-in Dinit user, **When** they view "my assigned
   tickets", **Then** only tickets where they are the ticket lead appear.

---

### User Story 6 - Role-aware access control (Priority: P3)

An admin manages user accounts and role assignments. Every ticket action —
creating, transitioning, commenting, approving — is gated by the acting
user's role, enforced by the system, not just hidden in the interface.

**Why this priority**: Roles are called out as core business value in the
constitution and doc 02 §2/§3, but as a cross-cutting concern this story is
last because it is exercised implicitly by Stories 1–5; this story specifically
covers the admin-facing user management surface.

**Independent Test**: As an admin, create a user and assign a role; log in as
that user and confirm only the actions permitted for that role are available
and that directly calling a disallowed action is rejected server-side.

**Acceptance Scenarios**:

1. **Given** an admin creates a new user with role `DINIT_USER`, **When** that
   user logs in, **Then** they can view and transition tickets assigned to
   Dinit but cannot approve proposals or manage other users.
2. **Given** a `CLIENT_USER`, **When** they attempt an admin-only action (e.g.
   creating another user), **Then** the system rejects it regardless of
   whether a UI control was available to trigger it.
3. **Given** a `CLIENT_USER` belonging to Organization A, **When** they list
   tickets or view the dashboard, **Then** they only see tickets belonging to
   Organization A — tickets from Organization B are neither listed nor
   directly accessible by ID.
4. **Given** a `DINIT_MANAGER`, **When** they view the dashboard, **Then**
   counts and lists aggregate across all Organizations, not just one.

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
  is already `CLOSED`? (Default: rejected — closed tickets are terminal
  except for an explicit re-open action, which is out of scope for MVP.)
- What happens if a client approver account is deleted while a proposal is
  still `PENDING`? (Default: out of scope for MVP — user deactivation/deletion
  is not part of this feature; assume users are not deleted mid-flow.)
- How does the dashboard behave with zero tickets (fresh install)? (Default:
  all cards show zero/empty states, no errors.)
- What happens when a Dinit ticket lead is assigned a ticket from an
  Organization they haven't worked with before? (Default: no restriction —
  any Dinit-side role can be assigned to any Organization's ticket; Dinit
  staff are not partitioned by client.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support ticket types `CHANGE_REQUEST`, `TASK`, and
  `DEFECT`, each with its own status lifecycle and its own set of legal
  transitions between statuses.
- **FR-002**: System MUST reject any status transition that is not explicitly
  allowed for the ticket's type and current status, regardless of who
  requests it.
- **FR-003**: System MUST require a Change Proposal (with a decision of
  approve or reject, decided by a `CLIENT_APPROVER`) before a `CHANGE_REQUEST`
  can proceed from `PROPOSAL` to `DEVELOPMENT`. `TASK` and `DEFECT` tickets
  MUST NOT have a proposal step.
- **FR-004**: System MUST require a severity (`SEV_1`–`SEV_4`) on every
  `DEFECT` ticket and MUST calculate `responseDueAt`, `firstInfoDueAt`, and
  `nextUpdateDueAt` from that severity per the formulas in doc 02 §4.
- **FR-005**: System MUST expose a computed `slaStatus`
  (`OK`/`DUE_SOON`/`BREACHED`/`NOT_APPLICABLE`) for every `DEFECT` ticket,
  derived from current time vs. the calculated deadlines, without requiring a
  manual recalculation step.
- **FR-006**: System MUST track `currentResponsibility` (`CLIENT` or `DINIT`)
  on every ticket and update it automatically on relevant status transitions
  (e.g. switches to `CLIENT` while a proposal is pending approval or a defect
  fix awaits confirmation).
- **FR-007**: System MUST enforce role-based permissions server-side for every
  state-changing action (create, transition, comment, approve/reject
  proposal, manage users) — a disallowed action must be rejected even if
  invoked directly, not just hidden from the UI.
- **FR-008**: System MUST support at minimum the roles `ADMIN`,
  `CLIENT_USER`, `CLIENT_APPROVER`, `DINIT_USER`, `DINIT_MANAGER`.
- **FR-009**: System MUST allow comments on a ticket with a visibility of
  `INTERNAL` (Dinit-only) or `PUBLIC` (client-visible), and MUST NOT expose
  `INTERNAL` comments to `CLIENT_USER`/`CLIENT_APPROVER` roles.
- **FR-010**: System MUST record an audit log entry (actor, action, old/new
  value, timestamp) for every status transition, field change (assignee,
  severity, responsibility), comment addition, and proposal decision.
- **FR-011**: System MUST record a status-history entry for every status
  transition on a ticket, viewable as a timeline on that ticket.
- **FR-012**: System MUST support filtering the ticket list by type, status,
  severity, assignee ("assigned to me"), current responsibility, SLA status,
  and free-text search.
- **FR-013**: System MUST provide a dashboard showing, at minimum: active vs.
  closed ticket counts, tickets by type, tickets by status, defects by
  severity, SLA-breached defects, SLA-due-soon defects, tickets waiting for
  client approval, tickets waiting for client confirmation, and the current
  user's assigned tickets.
- **FR-014**: System MUST allow attaching supporting documentation to a
  ticket, at minimum as file metadata (name, type, size) associated with the
  ticket and uploader.
- **FR-015**: System MUST authenticate users before granting access to any
  ticket data or action, using a stateless token (JWT) issued at login and
  presented on subsequent requests — no server-side session store.
- **FR-016**: Every ticket MUST carry a priority field with values `LOW` /
  `MEDIUM` / `HIGH` / `CRITICAL`, settable by Dinit-side roles, shown on the
  ticket list/detail, and filterable. Priority is informational only — it
  does not drive SLA calculation or gate any status transition (severity
  already governs urgency for defects).
- **FR-017**: System MUST support multiple client Organizations with data
  isolation between them: a `CLIENT_USER`/`CLIENT_APPROVER` MUST only see
  tickets, comments, and dashboard data belonging to their own Organization.
  Dinit-side roles (`DINIT_USER`, `DINIT_MANAGER`, `ADMIN`) are not bound to
  a single Organization and MUST be able to see and act across all
  Organizations' tickets, since Dinit is the vendor serving all of them.
- **FR-018**: Every ticket MUST belong to exactly one Organization, inherited
  from its business owner's Organization at creation time and immutable
  thereafter.

### Key Entities

- **Organization**: A client company using the tool. Attributes: name,
  active flag. `CLIENT_USER`/`CLIENT_APPROVER` accounts and the tickets their
  members create belong to exactly one Organization. Dinit-side roles are not
  scoped to an Organization.
- **User**: An account with a role; distinguishes client-side people from
  Dinit-side people. Attributes: name, email, role, active flag, Organization
  (required for client-side roles, absent for Dinit-side roles).
- **Ticket**: The central record. Attributes: type, status, priority,
  severity (defects only), title/description, business owner, ticket lead,
  assigned team, Organization, current responsibility, created/updated/closed
  timestamps, SLA deadline fields and status (defects only).
- **ChangeProposal**: A scope/estimate proposal tied to a `CHANGE_REQUEST`
  ticket, with its own approve/reject decision, decided-by user, and decision
  timestamp. Exists only for Change Requests.
- **Comment**: A message on a ticket with an author, body, visibility
  (internal/public), and timestamp.
- **Attachment**: A file (or file metadata) tied to a ticket, with uploader,
  filename, content type, and size.
- **AuditLog**: An immutable record of an action taken on a ticket — actor,
  action type, field changed (if any), old/new value, timestamp.
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
- **SC-004**: A Dinit manager can identify every SLA-breached and due-soon
  defect from a single dashboard view, without opening individual tickets.
- **SC-005**: A `CLIENT_USER` account can never successfully perform a
  Dinit-only or approver-only action (verified by attempting each disallowed
  action directly, not just checking the UI hides the button).
- **SC-006**: The 13-step demo story in doc 02 §12 can be performed start to
  finish by a presenter in under 10 minutes.
- **SC-007**: A Task can be taken from submission to closed without a
  proposal ever being created or required, in fewer status transitions than
  the equivalent Change Request flow.
- **SC-008**: With two client Organizations seeded, a user from Organization
  A can never see, list, or open a ticket belonging to Organization B, while
  a Dinit manager sees both in the same dashboard view.

## Assumptions

- `DISPUTE` ticket type is out of scope for MVP (doc 02 marks it optional);
  the type enum and lifecycle tables are designed so it can be added later
  without restructuring existing types.
- The simplified 5-role set from doc 02 §2 (`ADMIN`, `CLIENT_USER`,
  `CLIENT_APPROVER`, `DINIT_USER`, `DINIT_MANAGER`) is used rather than the
  fuller 7-role set — sufficient to demonstrate role-based business value
  without overbuilding permission granularity.
- Attachments are metadata-only for MVP (filename/type/size/uploader
  recorded; actual file bytes are not stored) per doc 02 §5's explicit
  allowance.
- SLA business-hours handling uses the simplified approximation in doc 02
  §4 ("MVP SLA implementation"), not a full business-calendar engine with
  holiday awareness.
- The system is multi-tenant: it can host multiple client Organizations at
  once, each with its own Contributors and Approvers, isolated from each
  other. Dinit-side roles work across all Organizations (per FR-017/FR-018).
  Organization management (create/rename/deactivate) is an `ADMIN`-only
  action; org self-service signup is out of scope for MVP.
- Ticket numbering/keys and pagination/page-size defaults are left to the
  implementation plan as standard patterns, not called out here since they
  don't materially change scope or user experience.
