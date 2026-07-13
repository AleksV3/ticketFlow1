# Tickets

## `GET /api/tickets`

Requires `TICKET_READ`. Lists tickets visible to the caller. CLIENT-party
callers are always implicitly filtered to their own Organization (FR-020) —
this cannot be overridden by query params.

**Query params** (all optional, combinable):

| Param | Values | Notes |
|---|---|---|
| `type` | a ticket type key (seeded defaults `CHANGE_REQUEST` \| `TASK` \| `DEFECT`, plus any admin-defined type) | |
| `status` | a workflow state key for the matching type's workflow (e.g. `ANALYSIS`) | states come from the type's configured workflow, not a fixed list |
| `severity` | `SEV_1`..`SEV_4` | `400` when combined with an explicitly non-DEFECT `type` |
| `priority` | `LOW`..`CRITICAL` | |
| `assignedTo` | `me` \| `unassigned` | `me` resolves to caller's user id as `ticketLeadId`; `unassigned` matches tickets with no `ticketLead` set (added by 002-ticket-master-detail for the "Unassigned" saved view) |
| `responsibility` | `CLIENT` \| `TICKETFLOW1` | maps to `currentResponsibility` |
| `slaStatus` | `OK` \| `DUE_SOON` \| `BREACHED` \| `NOT_APPLICABLE` | computed field, see research.md |
| `organizationId` | integer | TICKETFLOW1-party callers only; ignored (not rejected) if sent by a CLIENT-party caller, since their scope is already fixed |
| `q` | free text | matches against `title`, `description`, `ticketKey` |
| `page`, `pageSize` | see contracts/README.md | |

**Response `200`**: paginated envelope of `TicketSummary`:

```json
{
  "items": [
    {
      "id": 1042,
      "ticketKey": "TF-1042",
      "type": "DEFECT",
      "status": "ANALYSIS",
      "priority": "HIGH",
      "severity": "SEV_1",
      "title": "Checkout fails for EU customers",
      "organizationName": "ClientCo",
      "businessOwnerName": "Jane Client",
      "ticketLeadName": "Alex TicketFlow1",
      "currentResponsibility": "TICKETFLOW1",
      "slaStatus": "DUE_SOON",
      "createdAt": "2026-07-01T09:00:00Z",
      "updatedAt": "2026-07-02T08:30:00Z"
    }
  ],
  "page": 0, "pageSize": 20, "totalItems": 1, "totalPages": 1
}
```

## `POST /api/tickets`

Requires `TICKET_CREATE`. A CLIENT-party caller's `organizationId` is taken
from their token (the ticket is owned by their own Organization); a
TICKETFLOW1-party caller creating on a client's behalf must supply
`organizationId` explicitly.

**Request**:

```json
{
  "type": "CHANGE_REQUEST",
  "title": "Add SSO login support",
  "description": "We need SAML-based SSO for our enterprise users.",
  "priority": "MEDIUM",
  "organizationId": 3
}
```

`severity` is rejected (`400 VALIDATION_FAILED`) unless the type is `DEFECT`,
and required when it is.

**Response `201`**: full `TicketDetail` (see `GET /api/tickets/{ticketKey}`
below). The new ticket starts in its type's workflow initial state (the
seeded defaults use `SUBMITTED` for `CHANGE_REQUEST`/`TASK` and `REPORTED` for
`DEFECT`); a `TICKET_CREATED` audit log entry and an initial `StatusHistory`
row (`fromStatus=null`) are written in the same transaction.

## `GET /api/tickets/{ticketKey}`

**Response `200`** — `TicketDetail`:

```json
{
  "id": 1042,
  "ticketKey": "TF-1042",
  "type": "DEFECT",
  "status": "ANALYSIS",
  "priority": "HIGH",
  "severity": "SEV_1",
  "title": "Checkout fails for EU customers",
  "description": "...",
  "organization": { "id": 3, "name": "ClientCo" },
  "businessOwner": { "id": 41, "displayName": "Jane Client" },
  "ticketLead": { "id": 58, "displayName": "Alex TicketFlow1" },
  "assignedTeam": "Service Desk",
  "currentResponsibility": "TICKETFLOW1",
  "createdAt": "2026-07-01T09:00:00Z",
  "updatedAt": "2026-07-02T08:30:00Z",
  "closedAt": null,
  "sla": {
    "responseDueAt": "2026-07-01T09:15:00Z",
    "firstInfoDueAt": "2026-07-01T09:45:00Z",
    "nextUpdateDueAt": "2026-07-02T11:00:00Z",
    "respondedAt": "2026-07-01T09:10:00Z",
    "firstInfoAt": null,
    "status": "DUE_SOON"
  },
  "allowedTransitions": ["FIX_IN_PROGRESS", "CANCELLED"]
}
```

`sla` is omitted (not null — absent) for non-`DEFECT` tickets.
`allowedTransitions` is pre-filtered by the workflow engine to the moves the
caller may actually fire from the current state — those defined in the type's
workflow whose `required_permission` the caller holds (and whose
`required_party` matches) and whose `operationKind` is `STANDARD`. Proposal
commands are exposed separately in the proposal portion of `TicketDetail`.
The frontend renders exactly these standard moves as buttons,
never computing legality client-side (constitution Principle I/III).

**Errors**: `404 NOT_FOUND` (nonexistent, or exists but outside caller's org).

## `PATCH /api/tickets/{ticketKey}`

Requires `TICKET_UPDATE`. For non-status field edits only (title, description,
priority, ticketLead, assignedTeam). Status changes MUST go through
`POST .../transition` — this endpoint returns `400 VALIDATION_FAILED` if a
`status` field is present in the body, to keep the two concerns from being
silently conflated.

**Request** (all fields optional, only supplied ones are changed):

```json
{ "priority": "CRITICAL", "ticketLeadId": 58 }
```

**Response `200`**: updated `TicketDetail`. Each changed field writes its own
`AuditLog` entry (`ASSIGNEE_CHANGED`, `PRIORITY_CHANGED`, etc. — FR-013).

**Errors**: `403 FORBIDDEN` if the caller lacks the permission for this field
(e.g. reassigning `ticketLeadId` is a TICKETFLOW1-party action; setting
priority requires `TICKET_UPDATE` and TICKETFLOW1 party per FR-019).

## `POST /api/tickets/{ticketKey}/transition`

Requires `TICKET_TRANSITION` (the specific move may require a different
permission — the workflow engine checks each transition's own
`required_permission` and `required_party`; e.g. the CLIENT confirmation step
on a defect requires the caller be CLIENT party).

**Request**:

```json
{ "toStatus": "ANALYSIS", "comment": "Beginning triage." }
```

`comment` is optional; if present, it's stored as a `PUBLIC` comment in the
same transaction as the transition.

**Response `200`**: updated `TicketDetail`.

**Errors**: `409 ILLEGAL_TRANSITION` if the ticket type's workflow defines no
transition from the current state to `toStatus` (an undefined move);
`403 FORBIDDEN` if the transition exists but the caller lacks its
`required_permission` or `required_party` (see plan.md state diagrams, which
annotate each transition `PERMISSION [party]`); `409 INVALID_STATE` if the
matching transition is a protected proposal operation that must be performed
through the proposal endpoint; `409 CONFLICT` for a stale concurrent update.

Entering a terminal state sets `closedAt`; a configured reopen transition clears
it. A supplied comment and the state/history/audit mutation commit atomically.
