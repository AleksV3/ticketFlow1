# Ticket API Extensions

These contracts extend `specs/001-ticketing-mvp/contracts/tickets.md`. Existing
fields and endpoints remain compatible unless explicitly described below.

## Creation form

`GET /api/reference/ticket-types/{typeId}/creation-form` requires
`TICKET_CREATE`. The server derives caller scope and returns only active types,
subtypes, fields, and options the caller may use.

```json
{
  "type": { "id": 10, "key": "TASI", "name": "Technical Assistance" },
  "subtypes": [{
    "id": 31, "key": "FIREWALL", "name": "Firewall", "version": 2,
    "fields": [{
      "id": 80, "key": "source", "label": "Source", "kind": "SHORT_TEXT",
      "required": true, "visibility": "INTERNAL", "maxLength": 255,
      "options": []
    }]
  }]
}
```

Client requests for internal TASI/USR definitions return `404`. Inactive and
out-of-scope IDs are never included.

## Create ticket

`POST /api/tickets` adds `subtypeId`, `dynamicValues`, `parentTicketKey`, and
`targetUserId`. Standard assignment fields (`ticketLeadId`, `developerIds`,
and `teamIds`) remain available to callers with assignment authority.

```json
{
  "type": "TASI",
  "subtypeId": 31,
  "title": "Permit payroll API traffic",
  "description": "Production integration request",
  "priority": "HIGH",
  "organizationId": 3,
  "teamIds": [12],
  "developerIds": [44],
  "parentTicketKey": null,
  "targetUserId": null,
  "dynamicValues": {
    "source": "10.20.0.0/24",
    "destination": "payroll.internal",
    "service_ports": "TCP/443",
    "direction": "OUTBOUND",
    "environment": "PRODUCTION",
    "business_justification": "Required for payroll processing"
  }
}
```

The service loads one active definition snapshot, validates every value, and
persists ticket, typed values, routing, initial history, and audit atomically.
Unknown/hidden fields, inactive options, kind mismatches, cross-scope
references, and missing required values return field-level `400`. Authorized
manual assignment fields are audited through normal ticket assignment history.

USR `MODIFY`/`DELETE` require `targetUserId`; `NEW` rejects it. The server also
accepts `targetUserId` / `target_user_id` inside `dynamicValues` for form
compatibility, then stores the selected `app_user.id` plus a display snapshot.
DFCT alone accepts and requires fixed severity when its type has `DEFECT_SLA`
capability.

## Target-user search

`GET /api/reference/users?q={query}&purpose=USR_TARGET&organizationId={id}`
requires `TICKET_CREATE`, at least 2 trimmed characters, and returns at most 20
active users as `{ id, displayName, email }`. Client callers are locked to their
token organization. Internal callers may search only the explicitly selected
ticket organization. Out-of-scope and nonexistent scopes return the same empty
or `404` behavior and never reveal counts.

## Subtickets

Subtickets use the normal `POST /api/tickets` body with `parentTicketKey`.
Backend checks both tickets' scope and requested type availability, blocks
self-parenting and ancestor cycles, and permits only this inheritance allowlist
when omitted: organization and business owner. Team, developer, and priority
never inherit implicitly.

Parent detail includes `parentTicketKey` on children and `childTickets` on the
parent. Closing a parent with non-terminal children returns `409 OPEN_CHILDREN`.

## Detail, list, and board additions

Ticket detail adds subtype, authorized ordered dynamic values, routing rule ID,
resolved approver ID, parent, child tickets, and workflow commands. Internal
values are omitted for clients. Lists/boards add subtype and parent metadata
but not full dynamic values. Implemented filters include `type`, `status`,
`priority`, `severity`, `slaStatus`, `organizationId`, `parentTicketKey`, and
text query `q`.

`allowedTransitions` continues to contain STANDARD moves only. Protected
actions are returned under `workflowCommands`, for example
`CORRECTION_RETURN`, `WORKFLOW_APPROVE`, `WORKFLOW_REJECT`, `CLIENT_ACCEPT`, or
`CLIENT_REJECT`, only when all permission, party, tenant, relationship,
assignment, and current-state checks pass.

## Protected decisions

- `POST /api/tickets/{key}/correction-return` with required `{ "reason": "..." }`
- `POST /api/tickets/{key}/workflow-approve`
- `POST /api/tickets/{key}/workflow-reject` with required reason
- `POST /api/tickets/{key}/client-accept`
- `POST /api/tickets/{key}/client-reject` with required reason

Each uses the exact rule in `../workflow-matrix.md`, rejects stale state with
`409`, and persists decision/reason, transition, history, responsibility, and
audit in one transaction. Protected edges return `409 INVALID_STATE` through
the generic transition endpoint.
