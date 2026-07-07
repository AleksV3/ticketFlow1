# Audit Log & Status History

Both are read-only, append-only, ordered `createdAt` ascending. No write
endpoints — entries are only ever created as a side effect of other actions
(FR-013, FR-014). Both require `TICKET_READ`.

## `GET /api/tickets/{ticketKey}/audit-log`

**Response `200`**:

```json
[
  {
    "id": 901,
    "actor": { "id": 58, "displayName": "Alex TicketFlow1" },
    "action": "SEVERITY_CHANGED",
    "fieldName": "severity",
    "oldValue": "SEV_2",
    "newValue": "SEV_1",
    "createdAt": "2026-07-01T09:30:00Z"
  }
]
```

## `GET /api/tickets/{ticketKey}/status-history`

**Response `200`**:

```json
[
  {
    "id": 1201,
    "fromStatus": null,
    "toStatus": "REPORTED",
    "changedBy": { "id": 41, "displayName": "Jane Client" },
    "createdAt": "2026-07-01T09:00:00Z"
  },
  {
    "id": 1202,
    "fromStatus": "REPORTED",
    "toStatus": "ANALYSIS",
    "changedBy": { "id": 58, "displayName": "Alex TicketFlow1" },
    "createdAt": "2026-07-01T09:20:00Z"
  }
]
```

Both endpoints are visible to anyone who can view the ticket itself
(`TICKET_READ`) — unlike comments, there is no internal/public split for audit
or history (these are procedural facts, not communication).
