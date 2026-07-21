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

`POST /api/tickets` adds `subtypeId`, `configurationVersion`, `fieldValues`,
`parentTicketKey`, and an optional authorized `assignmentOverride`.

```json
{
  "type": "TASI",
  "subtypeId": 31,
  "configurationVersion": 2,
  "title": "Permit payroll API traffic",
  "description": "Production integration request",
  "priority": "HIGH",
  "organizationId": 3,
  "parentTicketKey": null,
  "fieldValues": {
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
Unknown/hidden fields, inactive options, kind mismatches, stale configuration,
cross-scope references, and missing required values return field-level `400`.
An authorized assignment override requires `TICKET_ASSIGN` and is audited.

USR `MODIFY`/`DELETE` require `target_user`; `NEW` rejects it. The server stores
the selected `app_user.id` plus a display snapshot. DFCT alone accepts and
requires fixed severity when its type has `DEFECT_SLA` capability.

## Target-user search

`GET /api/reference/users?q={query}&purpose=USR_TARGET&organizationId={id}`
requires `TICKET_CREATE`, at least 2 trimmed characters, and returns at most 20
active users as `{ id, displayName, email }`. Client callers are locked to their
token organization. Internal callers may search only the explicitly selected
ticket organization. Out-of-scope and nonexistent scopes return the same empty
or `404` behavior and never reveal counts.

## Subtickets

`POST /api/tickets/{parentKey}/children` uses the normal create body without
`parentTicketKey`; the path supplies the parent. Backend checks both tickets'
scope and requested type availability, blocks cycles, and permits only this
inheritance allowlist when omitted: organization and business owner. Team,
developer, and priority never inherit implicitly.

`GET /api/tickets/{key}/children?page=0&pageSize=20` returns visible summaries.
Parent detail includes `{ id, ticketKey }` and child progress counts. Closing a
parent with non-terminal children returns `409 OPEN_CHILDREN`.

## Detail, list, and board additions

Ticket detail adds subtype, authorized ordered field values, routing source,
resolved approver, parent, child progress, and `commands`. Internal values are
omitted for clients. Lists/boards add subtype and parent metadata but not full
dynamic values. New filters: `subtype`, `parent=none|{ticketKey}`, and
`hasOpenChildren=true|false`.

`allowedTransitions` continues to contain STANDARD moves only. Protected
actions are returned under `commands`, for example `SUBMIT_APPROVAL`,
`APPROVE`, `REJECT`, `ACCEPT`, or `RETURN_FOR_CORRECTION`, only when all
permission, party, tenant, relationship, assignment, and current-state checks
pass.

## Protected decisions

- `POST /api/tickets/{key}/approval/submit`
- `POST /api/tickets/{key}/approval/approve`
- `POST /api/tickets/{key}/approval/reject` with required `{ "reason": "..." }`
- `POST /api/tickets/{key}/acceptance/approve`
- `POST /api/tickets/{key}/acceptance/reject` with required reason
- `POST /api/tickets/{key}/return-for-correction` with required `toStatus` and
  `reason`

Each uses the exact rule in `../workflow-matrix.md`, rejects stale state with
`409`, and persists decision/reason, transition, history, responsibility, and
audit in one transaction. Protected edges return `409 INVALID_STATE` through
the generic transition endpoint.

