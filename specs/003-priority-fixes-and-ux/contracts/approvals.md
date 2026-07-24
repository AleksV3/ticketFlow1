# Approval Contracts

## Ticket detail commands

`GET /api/tickets/{ticketKey}` remains the UI source of truth.
`workflowCommands` includes `WORKFLOW_APPROVE` and/or `WORKFLOW_REJECT` only if
the current actor passes current-state, tenant, party, permission, and either
normal relationship or global-approval checks.

## Decide

- `POST /api/tickets/{ticketKey}/workflow-approve`
- `POST /api/tickets/{ticketKey}/workflow-reject`

Request:

```json
{ "reason": "Optional for approval; required for rejection", "version": 7 }
```

Eligibility is:

```text
same tenant/scope
AND active pending approval
AND expected ticket/approval version
AND valid protected workflow edge
AND (
  actor is the explicit resolved approver
  OR actor is the required assigned-team leader
  OR actor has APPROVE_ALL_TICKETS
)
```

A successful response is updated ticket detail. The transaction creates one
decision record, changes state, records history/audit, and stores the reason
with constitution-compatible visibility.

`403` means the visible ticket is pending but the actor is not eligible. `404`
prevents cross-tenant discovery. `409` covers stale version, an already decided
approval, or a no-longer-valid pending state.
