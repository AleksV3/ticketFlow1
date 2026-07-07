# Admin: Users, Organizations, Roles/Permissions & Ticket Types/Workflows

The mutating endpoints here are gated by configuration permissions —
`USER_MANAGE` (users, organizations), `ROLE_MANAGE` (roles, permission
catalog), `TYPE_MANAGE` (ticket types), `WORKFLOW_MANAGE` (workflows). The
`GET` list endpoints are broader — see each. Every config mutation writes a
`CONFIG_CHANGED` audit entry (FR-013). Client-scoped configuration (roles,
ticket types, workflows) is cloned per Organization from the seeded templates,
so an edit affects only the Organization that owns the row (FR-022).

## `GET /api/admin/users`

Any TICKETFLOW1-party user may call this (it populates the ticket-lead picker
for the 002-ticket-master-detail flow); the same widening `GET
/api/admin/organizations` has for the ticket-creation org picker. Managing
users (create/edit) still requires `USER_MANAGE`.

**Query params**: `organizationId` (optional filter), `roleId` (optional
filter), `page`/`pageSize`.

**Response `200`**: paginated `User`:

```json
{
  "items": [
    {
      "id": 41,
      "email": "approver@clientco.com",
      "displayName": "Jane Client",
      "party": "CLIENT",
      "roleId": 7,
      "roleName": "Client Approver",
      "organizationId": 3,
      "organizationName": "ClientCo",
      "active": true,
      "createdAt": "2026-06-01T00:00:00Z"
    }
  ],
  "page": 0, "pageSize": 20, "totalItems": 1, "totalPages": 1
}
```

## `POST /api/admin/users`

Requires `USER_MANAGE`.

**Request**:

```json
{
  "email": "newuser@clientco.com",
  "password": "temporary-onboarding-password",
  "displayName": "New Client User",
  "roleId": 6,
  "organizationId": 3
}
```

The assigned role's party must match the user's party (a CLIENT-party role
cannot be assigned to a TICKETFLOW1-party user). `organizationId` is required
when the assigned role is a CLIENT-party role and rejected (`400
VALIDATION_FAILED`) for a TICKETFLOW1-party role (data-model.md validation
rules).

**Response `201`**: created `User` (password never echoed back).

## `GET /api/admin/organizations`

Any TICKETFLOW1-party user may call this.

**Response `200`**: array of `Organization`:

```json
[ { "id": 3, "name": "ClientCo", "active": true, "createdAt": "2026-06-01T00:00:00Z" } ]
```

## `POST /api/admin/organizations`

Requires `USER_MANAGE`.

**Request**: `{ "name": "NewClientCo" }`

**Response `201`**: created `Organization`, `active: true`. On creation the
seeded client-scoped templates (roles, ticket types, workflows) are cloned for
the new Organization so it is immediately usable (FR-022).

## `PATCH /api/admin/organizations/{id}`

Requires `USER_MANAGE`.

**Request**: `{ "name": "Renamed Co", "active": false }` (both optional).

**Response `200`**: updated `Organization`. Deactivating an org does not
delete or hide its existing tickets/users — it only blocks new logins from
that org's users (service-layer check at login), per spec Assumptions
("org self-service signup is out of scope"; deactivation is the only
lifecycle op needed for MVP demo purposes).

---

# Roles & Permissions

The permission catalog is fixed and code-owned — it can be listed but not
edited at runtime (FR-008). Roles are configurable bundles of catalog
permissions (FR-009): an admin holding `ROLE_MANAGE` can create a role and
edit its name and permission set, and the change takes effect on the assigned
users' next request with no code deployment. A role's `party` (`CLIENT` vs
`TICKETFLOW1`) is structural and fixed at creation — it can never be changed to
grant cross-party visibility (FR-010).

## `GET /api/admin/permissions`

Requires `ROLE_MANAGE`. Lists the fixed permission catalog (reference data),
so the role editor can present the full set of grantable permissions.

**Response `200`**:

```json
[
  { "id": 1, "key": "TICKET_READ" },
  { "id": 2, "key": "TICKET_CREATE" },
  { "id": 3, "key": "TICKET_UPDATE" },
  { "id": 4, "key": "TICKET_TRANSITION" },
  { "id": 5, "key": "PROPOSAL_APPROVE" },
  { "id": 6, "key": "COMMENT_PUBLIC_WRITE" },
  { "id": 7, "key": "COMMENT_INTERNAL_WRITE" },
  { "id": 8, "key": "USER_MANAGE" },
  { "id": 9, "key": "ROLE_MANAGE" },
  { "id": 10, "key": "TYPE_MANAGE" },
  { "id": 11, "key": "WORKFLOW_MANAGE" }
]
```

## `GET /api/admin/roles`

Requires `ROLE_MANAGE`. Lists roles with their granted permission keys.
CLIENT-party roles are scoped to an Organization (`organizationId` set);
TICKETFLOW1-party roles are global (`organizationId` null). `isTemplate = true`
marks the seed rows new Organizations clone from.

**Query params**: `organizationId` (optional filter), `party` (optional
filter, `CLIENT` | `TICKETFLOW1`).

**Response `200`**:

```json
[
  {
    "id": 7,
    "name": "Client Approver",
    "party": "CLIENT",
    "organizationId": 3,
    "isTemplate": false,
    "permissions": ["TICKET_READ", "TICKET_CREATE", "COMMENT_PUBLIC_WRITE", "PROPOSAL_APPROVE"]
  },
  {
    "id": 12,
    "name": "TicketFlow1 Manager",
    "party": "TICKETFLOW1",
    "organizationId": null,
    "isTemplate": true,
    "permissions": ["TICKET_READ", "TICKET_CREATE", "TICKET_UPDATE", "TICKET_TRANSITION", "COMMENT_PUBLIC_WRITE", "COMMENT_INTERNAL_WRITE", "USER_MANAGE"]
  }
]
```

## `POST /api/admin/roles`

Requires `ROLE_MANAGE`. Creates a new role from a chosen subset of catalog
permissions.

**Request**:

```json
{
  "name": "Reviewer",
  "party": "CLIENT",
  "organizationId": 3,
  "permissions": ["TICKET_READ", "COMMENT_PUBLIC_WRITE"]
}
```

`organizationId` is required for a `CLIENT`-party role and must be `null` for a
`TICKETFLOW1`-party role. Every entry in `permissions` must be a key present in
the fixed catalog.

**Response `201`**: created role.

```json
{
  "id": 21,
  "name": "Reviewer",
  "party": "CLIENT",
  "organizationId": 3,
  "isTemplate": false,
  "permissions": ["TICKET_READ", "COMMENT_PUBLIC_WRITE"]
}
```

**Errors**: `400 VALIDATION_FAILED` if `permissions` contains a key not in the
catalog (the catalog is fixed; only role→permission mappings are editable —
FR-008), or if `organizationId` is inconsistent with `party`.

## `PATCH /api/admin/roles/{id}`

Requires `ROLE_MANAGE`. Edits a role's name and/or its full permission set. A
supplied `permissions` array replaces the role's grants wholesale. `party` and
`organizationId` are immutable — they are not accepted here.

**Request** (all fields optional):

```json
{
  "name": "Senior Reviewer",
  "permissions": ["TICKET_READ", "COMMENT_PUBLIC_WRITE", "TICKET_TRANSITION"]
}
```

**Response `200`**: updated role, same shape as `POST`. The change applies to
every assigned user on their next request. Writes a `CONFIG_CHANGED` audit
entry.

**Errors**: `400 VALIDATION_FAILED` (unknown permission key); `404 NOT_FOUND`
(no such role, or it belongs to another Organization the caller cannot manage).

---

# Ticket Types & Workflows

A ticket type is bound to one workflow; the workflow is the set of states and
the permitted transitions between them, each transition gated by a required
permission (and optionally a required party). Tickets of a type start in that
workflow's initial state, and the workflow engine rejects any move the workflow
does not define — identically for seeded and admin-defined types (FR-001,
FR-002). Seeded defaults are `CHANGE_REQUEST`, `TASK`, `DEFECT`.

## `GET /api/admin/ticket-types`

Requires `TYPE_MANAGE`. Lists ticket types with the workflow each is bound to.

**Query params**: `organizationId` (optional filter).

**Response `200`**:

```json
[
  {
    "id": 30,
    "key": "CHANGE_REQUEST",
    "name": "Change Request",
    "workflowId": 50,
    "organizationId": 3,
    "isTemplate": false,
    "requiresProposal": true
  },
  {
    "id": 31,
    "key": "TASK",
    "name": "Task",
    "workflowId": 51,
    "organizationId": 3,
    "isTemplate": false,
    "requiresProposal": false
  }
]
```

## `POST /api/admin/ticket-types`

Requires `TYPE_MANAGE`. Defines a new ticket type bound to an existing
workflow.

**Request**:

```json
{
  "key": "ACCESS_REQUEST",
  "name": "Access Request",
  "workflowId": 60,
  "organizationId": 3,
  "requiresProposal": false
}
```

`workflowId` must reference a workflow owned by the same Organization (or a
global template). `requiresProposal` may be `true` only if the referenced
workflow contains a proposal step.

**Response `201`**: created ticket type (same shape as the list items above).

**Errors**: `400 VALIDATION_FAILED` (duplicate `key` within the Organization,
or `requiresProposal = true` against a workflow with no proposal step);
`404 NOT_FOUND` (`workflowId` not visible to the caller).

## `GET /api/admin/workflows`

Requires `WORKFLOW_MANAGE`. Lists workflows with their states and
permission-keyed transitions. Each transition names the state it moves from and
to, the permission the actor must hold, and (when the step is party-specific)
the required party and the responsibility the ticket takes on afterward.

**Query params**: `organizationId` (optional filter).

**Response `200`**:

```json
[
  {
    "id": 51,
    "name": "Task Workflow",
    "organizationId": 3,
    "states": [
      { "id": 400, "key": "SUBMITTED", "isInitial": true, "isTerminal": false, "sortOrder": 1 },
      { "id": 401, "key": "ANALYSIS", "isInitial": false, "isTerminal": false, "sortOrder": 2 },
      { "id": 402, "key": "DEVELOPMENT", "isInitial": false, "isTerminal": false, "sortOrder": 3 },
      { "id": 403, "key": "CLOSED", "isInitial": false, "isTerminal": true, "sortOrder": 4 },
      { "id": 404, "key": "CANCELLED", "isInitial": false, "isTerminal": true, "sortOrder": 5 }
    ],
    "transitions": [
      { "id": 700, "fromStateId": 400, "toStateId": 401, "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null },
      { "id": 701, "fromStateId": 401, "toStateId": 402, "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null },
      { "id": 702, "fromStateId": 402, "toStateId": 403, "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null },
      { "id": 703, "fromStateId": 400, "toStateId": 404, "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null }
    ]
  }
]
```

## `POST /api/admin/workflows`

Requires `WORKFLOW_MANAGE`. Creates a workflow with its states and transitions
in one request. Exactly one state must be `isInitial`; at least one must be
`isTerminal`. Each transition references states by their `key` within this
request and names the permission that gates it.

**Request**:

```json
{
  "name": "Access Request Workflow",
  "organizationId": 3,
  "states": [
    { "key": "OPEN", "isInitial": true, "isTerminal": false, "sortOrder": 1 },
    { "key": "GRANTED", "isInitial": false, "isTerminal": false, "sortOrder": 2 },
    { "key": "CLOSED", "isInitial": false, "isTerminal": true, "sortOrder": 3 }
  ],
  "transitions": [
    { "fromState": "OPEN", "toState": "GRANTED", "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null },
    { "fromState": "GRANTED", "toState": "CLOSED", "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null }
  ]
}
```

**Response `201`**: created workflow with server-assigned integer ids for the
workflow, its states, and its transitions (same shape as the list items above).

**Errors**: `400 VALIDATION_FAILED` (no initial state, more than one initial
state, no terminal state, a transition referencing an unknown state key, or a
`requiredPermission` not in the catalog).

## `PATCH /api/admin/workflows/{id}`

Requires `WORKFLOW_MANAGE`. Edits a workflow's states and transitions. A
supplied `states`/`transitions` array replaces that collection wholesale;
omitted collections are left unchanged. The one-initial / at-least-one-terminal
rules are re-checked on the resulting definition.

**Request** (add a `GRANTED → OPEN` re-open transition):

```json
{
  "transitions": [
    { "fromState": "OPEN", "toState": "GRANTED", "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null },
    { "fromState": "GRANTED", "toState": "CLOSED", "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null },
    { "fromState": "GRANTED", "toState": "OPEN", "requiredPermission": "TICKET_TRANSITION", "requiredParty": "TICKETFLOW1", "responsibilityAfter": null }
  ]
}
```

**Response `200`**: updated workflow. Writes a `CONFIG_CHANGED` audit entry.
The new transition set takes effect immediately for new moves; tickets already
mid-flow keep their current state (workflow edits are assumed additive for MVP
per spec Assumptions).

**Errors**: `400 VALIDATION_FAILED` (the resulting definition breaks the
initial/terminal rules, references an unknown state key, or names a permission
outside the catalog); `404 NOT_FOUND` (no such workflow, or it belongs to an
Organization the caller cannot manage).
