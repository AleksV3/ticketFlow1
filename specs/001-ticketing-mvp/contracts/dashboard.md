# Dashboard

## `GET /api/dashboard`

Requires `TICKET_READ`. Scoped identically to `GET /api/tickets` —
CLIENT-party callers see only their own Organization's data; TICKETFLOW1-party
callers see everything (FR-016, FR-020, spec User Story 5).

**Response `200`**:

```json
{
  "activeCount": 42,
  "closedCount": 118,
  "byType": { "CHANGE_REQUEST": 15, "TASK": 20, "DEFECT": 7 },
  "byStatus": { "ANALYSIS": 5, "PROPOSAL": 3, "DEVELOPMENT": 8 },
  "defectsBySeverity": { "SEV_1": 1, "SEV_2": 2, "SEV_3": 3, "SEV_4": 1 },
  "slaBreached": [ /* TicketSummary[], see tickets.md */ ],
  "slaDueSoon": [ /* TicketSummary[] */ ],
  "waitingForClientApproval": [ /* TicketSummary[], status=PROPOSAL */ ],
  "waitingForClientConfirmation": [ /* TicketSummary[], status=CLIENT_CONFIRMATION */ ],
  "myAssignedTickets": [ /* TicketSummary[], ticketLeadId = caller.id */ ]
}
```

`myAssignedTickets` is empty for CLIENT-party callers, who are never a ticket
lead — included regardless of the caller for a uniform response shape, the
frontend simply won't render an empty card.

`slaBreached`/`slaDueSoon` are capped at 20 items each (dashboard is an
overview, not a full list — link through to the filtered ticket list for
the complete set via `GET /api/tickets?slaStatus=BREACHED`).

`activeCount`/`closedCount` use `WorkflowState.isTerminal`, not hard-coded state
names. The two waiting cards are intentionally seeded-workflow views in MVP:
`PROPOSAL` for Change Requests and `CLIENT_CONFIRMATION` for Defects. Custom
workflow semantic categories are deferred; custom states still appear in
`byStatus`.
