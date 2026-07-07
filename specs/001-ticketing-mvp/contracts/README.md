# API Contracts: TicketFlow1 Ticketing Tool — MVP

Base path: `/api`. All request/response bodies are JSON. All endpoints
except `POST /api/auth/login` require `Authorization: Bearer <jwt>`.

Files in this directory, grouped by resource:

- [auth.md](auth.md) — login, current user
- [tickets.md](tickets.md) — list/create/get/update/transition
- [comments.md](comments.md)
- [attachments.md](attachments.md)
- [proposals.md](proposals.md)
- [audit-and-history.md](audit-and-history.md)
- [dashboard.md](dashboard.md)
- [admin.md](admin.md) — users, organizations, roles/permissions, ticket types/workflows

## Conventions used across all endpoints

### Auth, permissions & organization scoping

Every authenticated request carries the caller's resolved **permission set**
and party (`CLIENT` | `TICKETFLOW1`), plus (for CLIENT-party callers)
`organizationId`, inside the JWT claims. The permission set comes from the role
assigned to the user; the server authorizes each action against the required
**permission**, never against a role's name. The server never trusts an
`organizationId` supplied in a request body/query for scoping — it is always
read from the token. See [data-model.md](../data-model.md) validation rules
and FR-007/FR-018/FR-020.

Each endpoint below states the **permission** it requires (e.g.
`TICKET_TRANSITION`, `PROPOSAL_APPROVE`, `USER_MANAGE`, `ROLE_MANAGE`,
`TYPE_MANAGE`, `WORKFLOW_MANAGE`) and, where the action is party-specific, the
required **party** (`CLIENT` vs `TICKETFLOW1`).

### Standard error response shape

Every 4xx/5xx response body:

```json
{
  "status": 409,
  "error": "ILLEGAL_TRANSITION",
  "message": "Cannot move ticket TF-1042 from PROPOSAL to DEVELOPMENT: proposal is not APPROVED.",
  "path": "/api/tickets/TF-1042/transition",
  "timestamp": "2026-07-02T10:15:30Z"
}
```

- `error` is a stable machine-readable code (upper snake case), not the
  exception class name — frontend code branches on `error`, never on
  `message` text.
- `message` is human-readable, safe to show in the UI.
- Validation errors (400) additionally include a `fieldErrors` array:

```json
{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request body failed validation.",
  "path": "/api/tickets",
  "timestamp": "2026-07-02T10:15:30Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

### Standard error codes

| HTTP | `error` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | request body fails bean validation |
| 401 | `UNAUTHENTICATED` | missing/invalid/expired JWT |
| 403 | `FORBIDDEN` | authenticated, but the caller lacks the required permission (or party) for this action |
| 404 | `NOT_FOUND` | resource doesn't exist, or exists but is outside caller's org scope (never distinguish the two — see below) |
| 409 | `ILLEGAL_TRANSITION` | the workflow engine has no transition from the ticket's current state to the requested one (undefined move) |
| 409 | `INVALID_STATE` | e.g. approving a proposal that's already decided |
| 500 | `INTERNAL_ERROR` | unexpected server error |

**Org-scoping returns 404, not 403, for out-of-org resources.** A CLIENT-party
caller requesting a ticket from another Organization gets the same
`404 NOT_FOUND` as a nonexistent ticket ID — this avoids confirming to a
client user that a given ticket ID exists in someone else's org.

### Pagination

List endpoints return:

```json
{
  "items": [ /* ... */ ],
  "page": 0,
  "pageSize": 20,
  "totalItems": 137,
  "totalPages": 7
}
```

Requested via `?page=0&pageSize=20` (0-indexed), default `pageSize=20`, max `100`.

### Timestamps & IDs

All timestamps are ISO-8601 UTC (`Instant`, serialized as
`2026-07-02T10:15:30Z`). Resource IDs are `bigint` integers (e.g. `1042`).
Tickets are addressed in URLs by their human-readable business key `ticketKey`
(e.g. `TF-1042`, matching how TicketFlow1 staff refer to tickets); every other
resource is addressed by its integer `id`.
