# Feature Specification: Configurable Service Request Workflows

**Feature Branch**: `002-service-request-workflows`

**Created**: 2026-07-21

**Status**: Approved for detailed design

**Input**: Add internal TASI and USR ticket processes, revise client DFCT and
REQ processes, provide configurable subtypes and subtype-specific forms,
automatically route work to responsible teams/developers, enforce relationship-
aware approvals, support parent/subtickets, and allow organizations to control
which ticket types their users may create.

## Terminology

- **Ticket type**: top-level process (`TASI`, `USR`, `DFCT`, `REQ`).
- **Subtype**: configurable classification below a ticket type (for example,
  `TASI/FIREWALL` or `USR/MODIFY`).
- **Field definition**: administrator-configured input shown for one subtype.
- **Routing rule**: mapping from type/subtype (optionally organization) to a
  default team, developer, and approval authority.
- **Return for correction**: non-terminal transition from analysis to the
  starting state. It requires a reason and permits another analysis loop.
- **Reject**: terminal business decision. It is distinct from returning a
  ticket for correction.
- **Signature ID**: the displayed ticket key, such as `TF-1000`.

## User Scenarios & Testing

### User Story 1 - Create an internal TASI with a subtype-specific form (P1)

An authorized TicketFlow1 user creates a TASI ticket, selects a configurable
technical subtype, completes the fields configured for that subtype, and sees
the ticket automatically routed to the correct delivery team and analyst.

**Acceptance scenarios**:

1. Given active TASI subtypes `FIREWALL`, `NETWORK`, `APPLICATION`, and
   `HARDWARE`, when the creator selects one, then only that subtype's active
   field definitions are returned and rendered in configured order.
2. Given `FIREWALL` requires source, destination, service/port, environment,
   and justification, when any required value is absent or has the wrong type,
   then the backend rejects ticket creation with field-level errors.
3. Given a routing rule for `TASI/FIREWALL`, when the ticket is created, then
   its default team and analyst are assigned atomically and recorded in audit.
4. Given a disabled or deleted subtype, when a caller submits its ID directly,
   then creation is rejected even if the UI no longer displays it.
5. Given a client user, when they request TASI creation or its form definition,
   then the system returns `404`/`403` without exposing internal configuration.

### User Story 2 - Create and process a USR request (P1)

An authorized internal user selects `NEW`, `MODIFY`, or `DELETE`. The form
changes based on the action. `MODIFY` and `DELETE` require selecting an existing
user through a tenant-safe autocomplete rather than entering an ambiguous name.

**Acceptance scenarios**:

1. `USR/NEW` requests identity and access details but does not require a target
   user reference.
2. `USR/MODIFY` and `USR/DELETE` require an existing target user ID and display
   an autocomplete restricted to the correct organization/directory scope.
3. The ticket stores the immutable target-user ID plus a display snapshot for
   historical readability; duplicate display names do not cause ambiguity.
4. A caller cannot use a manipulated request to select a target user from
   another organization.
5. Subtypes and form definitions can be added, renamed, reordered, disabled,
   and safely removed without a schema migration or physical ticket columns.

### User Story 3 - Execute TASI and USR approval workflows (P1)

The assigned analyst can return incomplete work to the start or submit analysis
to the assigned team's leader. Only the relationship-aware approver can approve
implementation.

**TASI and USR lifecycle**:

```text
NEW -> ANALYSIS
ANALYSIS -> NEW                    (return for correction; reason required)
ANALYSIS -> PENDING_APPROVAL
PENDING_APPROVAL -> ANALYSIS       (return for correction; reason required)
PENDING_APPROVAL -> IMPLEMENTATION
IMPLEMENTATION -> CLOSED           (terminal)
```

Approval rejection is a return for correction and may repeat. TASI and USR do
not end merely because an approver requests changes.

**Acceptance scenarios**:

1. `NEW -> ANALYSIS` assigns or confirms the routing-rule team and analyst.
2. `ANALYSIS -> NEW` requires a public/internal reason according to policy and
   can repeat without a loop limit; every iteration appears in history/audit.
3. `ANALYSIS -> PENDING_APPROVAL` preserves the analysis team/developer for
   implementation and resolves an approver from the assigned team leader or an
   explicit designated approver.
4. A user holding a generic transition permission cannot approve unless they
   are also the resolved approver for that ticket.
5. The analyst cannot self-approve unless they are explicitly the team leader
   or designated approver.
6. Approval/rejection records actor, timestamp, decision, and optional/required
   reason in the same transaction as the protected state transition.

### User Story 4 - Process client DFCT tickets (P1)

A client reports a defect. An assigned internal developer analyses it, may
return it for correction, develops a fix, and sends it through deployment.

```text
REPORTED -> ANALYSIS
ANALYSIS -> REPORTED               (return for correction; reason required)
ANALYSIS -> DEVELOPMENT
DEVELOPMENT -> DEPLOYMENT
DEPLOYMENT -> DEVELOPMENT          (deployment rejected; reason required)
DEPLOYMENT -> CLOSED               (deployment successful)
```

Client users may create DFCT only when that type is active for their
organization. Existing defect severity/SLA behavior must continue under the
new `DFCT` key or be migrated to a type capability rather than a literal key.

### User Story 5 - Process client REQ tickets with client acceptance (P1)

A client reports a request. TicketFlow1 analyses and implements it, and an
authorized client decision-maker accepts the proposed solution before
deployment.

```text
REPORTED -> ANALYSIS
ANALYSIS -> REPORTED               (return for correction; reason required)
ANALYSIS -> CLIENT_ACCEPTANCE
CLIENT_ACCEPTANCE -> ANALYSIS      (client rejects; reason required)
CLIENT_ACCEPTANCE -> IMPLEMENTATION (client approves)
IMPLEMENTATION -> DEPLOYMENT
DEPLOYMENT -> IMPLEMENTATION       (deployment rejected; reason required)
DEPLOYMENT -> CLOSED               (deployment successful)
```

Only the business owner or an explicitly delegated same-organization user
holding the configured client-approval permission may decide client
acceptance. Holding the permission without either relationship is insufficient.
A different client organization always receives `404`.

### User Story 6 - Configure type availability per organization (P1)

TicketFlow1 administrators control the types available to internal users and
to each client organization. New companies start with active `DFCT` and `REQ`
types only. Internal users can access all active internal types.

**Acceptance scenarios**:

1. Type references and create validation return only active types available in
   the caller's scope; the backend does not trust a type key supplied by UI.
2. Administrators can add a type to an organization, rename its display name,
   assign its workflow, and deactivate it.
3. An unused type may be permanently deleted. A referenced type may only be
   deactivated so historical tickets remain readable.
4. Deactivation blocks new tickets but does not hide existing tickets.
5. Configuration mutations are organization-scoped, permission-gated, and
   recorded in configuration audit.

### User Story 7 - Create and navigate subtickets (P2)

An authorized user creates a child ticket from a parent. Each child retains its
own type, subtype, workflow, assignments, comments, attachments, audit, and
signature ID, while inheriting a safe subset of parent context.

**Acceptance scenarios**:

1. Parent and child belong to the same organization/internal scope.
2. A client cannot create an internal child type or view an internal child.
3. Self-parenting and all direct/indirect cycles are rejected.
4. Parent deletion never silently deletes children, and parent closure is
   blocked while any child is non-terminal.
5. Parent detail lists children and progress; child detail links to the parent.
6. Creation may explicitly inherit organization, business owner, team, or
   lead, but the backend revalidates every inherited assignment.

### User Story 8 - Administer subtype forms and routing (P2)

An authorized administrator manages subtype definitions, dynamic fields,
select options, and routing rules without changing Java, React, or the physical
ticket table.

**Supported initial field kinds**: short text, long text, integer/decimal,
date, boolean, single select, multi-select, user reference, and team reference.

**Acceptance scenarios**:

1. Stable keys are immutable after use; display labels remain editable.
2. Select options can be reordered/deactivated; referenced options are not
   physically deleted.
3. Field values are validated and stored in typed value columns/relation rows;
   no runtime DDL or administrator-supplied SQL is permitted.
4. Routing rules cannot assign inactive/cross-scope users or teams.
5. Manual reassignment remains available to `TICKET_ASSIGN` users and is
   audited without changing the underlying routing rule.

## Functional Requirements

- **FR-001**: Support `TASI`, `USR`, `DFCT`, and `REQ` as distinct configurable
  ticket types with enforced workflows.
- **FR-002**: Support active, ordered, type-scoped subtype definitions.
- **FR-003**: Support type/subtype-specific dynamic form definitions and typed
  values without runtime schema changes.
- **FR-004**: Validate dynamic values exclusively from server-loaded active
  definitions and reject unknown fields/options.
- **FR-005**: Support deterministic subtype routing to team, primary developer,
  fallback developer, and approver.
- **FR-006**: Preserve analysis assignment into TASI/USR implementation unless
  an authorized reassignment is explicitly made.
- **FR-007**: Enforce approval using permission, party, tenant, and ticket/team
  relationship checks.
- **FR-008**: Persist approval decisions as domain records coupled atomically to
  protected transitions.
- **FR-009**: Require reasons on correction loops and rejection transitions.
- **FR-010**: Support organization-specific type availability and safe
  deactivate/delete behavior.
- **FR-011**: Support parent/child ticket relationships with cycle prevention.
- **FR-012**: Include type, subtype, parent, child progress, dynamic values,
  routing, and approval data in ticket detail/audit responses as authorized.
- **FR-013**: Add list/board filters for subtype and parent/child state.
- **FR-014**: Replace literal `DEFECT` behavior checks with a safe defect/SLA
  capability or migrate them comprehensively to `DFCT`.
- **FR-015**: Seed a usable default configuration while preserving existing
  tickets and historical data.

## Security and Audit Requirements

- Every read and mutation is tenant scoped on the backend.
- Client user/directory search never exposes another organization's people.
- Dynamic definitions are data only: no scripts, expressions, HTML, SQL, or
  runtime permission invention.
- Field text is length bounded and safely encoded; reference fields resolve by
  server-side IDs rather than trusted labels.
- Every configuration mutation, routing/reassignment, approval decision,
  dynamic-value change, parent link, and workflow transition is audited.
- Public audit/history never discloses internal fields, comments, routing
  notes, or target-user data not visible to the caller.

## Confirmed Product Decisions

1. USR target-user autocomplete searches existing TicketFlow1 `app_user`
   records. A future external directory may be added behind an adapter, but is
   not part of this feature.
2. TASI/USR approval rejection returns the ticket to `ANALYSIS`, requires a
   visible correction reason, and is repeatable; it is not terminal.
3. REQ acceptance may be decided by the client business owner or an explicitly
   delegated user from the same organization who holds the fixed client
   approval permission.
4. A parent ticket cannot close while any child ticket is non-terminal.

The initial templates and field visibility are defined in `field-templates.md`.
The exact transition actors and protected operations are defined in
`workflow-matrix.md`.

## Success Criteria

- A new subtype and form field can be configured and used without a deployment
  or physical database-column change.
- All four default workflows pass legal/illegal transition matrix tests,
  including repeated correction and deployment loops.
- Unauthorized approval attempts fail even when the caller has generic
  transition permission.
- Cross-organization type, user-search, field-value, routing, approval, and
  subticket access tests all return no foreign data.
- Existing tickets remain readable after migration and deactivation of their
  former type/subtype configuration.
