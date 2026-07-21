# Approved Workflow and Actor Matrix

This matrix is the implementation baseline for T006. The product owner approved
the workflow direction on 2026-07-21; final mentor acceptance remains part of
release task T068. Relationship checks are additional to permissions and party.

`TICKET_TRANSITION` covers ordinary work movement. `TICKET_APPROVE` and
`REQUEST_ACCEPT` are fixed, developer-owned permission keys to be introduced for
protected decisions; administrators may include them in configurable roles.

## TASI and USR

| From | To | Actor and relationship | Permission | Reason | Responsibility after | Operation |
|---|---|---|---|---|---|---|
| NEW | ANALYSIS | Routed internal analyst/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |
| ANALYSIS | NEW | Assigned analyst/team member | TICKET_TRANSITION | required | TICKETFLOW1 | CORRECTION_RETURN |
| ANALYSIS | PENDING_APPROVAL | Assigned analyst | TICKET_TRANSITION | no | TICKETFLOW1 | APPROVAL_SUBMIT |
| PENDING_APPROVAL | ANALYSIS | Assigned team leader/designated approver | TICKET_APPROVE | required | TICKETFLOW1 | APPROVAL_REJECT |
| PENDING_APPROVAL | IMPLEMENTATION | Assigned team leader/designated approver | TICKET_APPROVE | optional | TICKETFLOW1 | APPROVAL_APPROVE |
| IMPLEMENTATION | CLOSED | Assigned developer/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |

## DFCT

| From | To | Actor and relationship | Permission | Reason | Responsibility after | Operation |
|---|---|---|---|---|---|---|
| REPORTED | ANALYSIS | Assigned internal analyst/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |
| ANALYSIS | REPORTED | Assigned analyst/team member | TICKET_TRANSITION | required, client-visible | CLIENT | CORRECTION_RETURN |
| ANALYSIS | DEVELOPMENT | Assigned analyst/developer | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |
| DEVELOPMENT | DEPLOYMENT | Assigned developer/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |
| DEPLOYMENT | DEVELOPMENT | Assigned deployment actor/team member | TICKET_TRANSITION | required | TICKETFLOW1 | CORRECTION_RETURN |
| DEPLOYMENT | CLOSED | Assigned deployment actor/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |

## REQ

| From | To | Actor and relationship | Permission | Reason | Responsibility after | Operation |
|---|---|---|---|---|---|---|
| REPORTED | ANALYSIS | Assigned internal analyst/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |
| ANALYSIS | REPORTED | Assigned analyst/team member | TICKET_TRANSITION | required, client-visible | CLIENT | CORRECTION_RETURN |
| ANALYSIS | CLIENT_ACCEPTANCE | Assigned analyst | TICKET_TRANSITION | no | CLIENT | ACCEPTANCE_SUBMIT |
| CLIENT_ACCEPTANCE | ANALYSIS | Business owner or explicit same-org delegate | REQUEST_ACCEPT | required | TICKETFLOW1 | ACCEPTANCE_REJECT |
| CLIENT_ACCEPTANCE | IMPLEMENTATION | Business owner or explicit same-org delegate | REQUEST_ACCEPT | optional | TICKETFLOW1 | ACCEPTANCE_APPROVE |
| IMPLEMENTATION | DEPLOYMENT | Assigned developer/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |
| DEPLOYMENT | IMPLEMENTATION | Assigned deployment actor/team member | TICKET_TRANSITION | required | TICKETFLOW1 | CORRECTION_RETURN |
| DEPLOYMENT | CLOSED | Assigned deployment actor/team member | TICKET_TRANSITION | no | TICKETFLOW1 | STANDARD |

Protected operations never execute through the generic transition endpoint.
Every reason, decision, actor, relationship resolution, state change, history
entry, and audit entry is persisted atomically.

## Exact authorization rules

- `TICKET_APPROVE` is required for TASI/USR approve and reject; the actor must
  also be TICKETFLOW1 and equal the ticket's resolved approver. Resolution is
  the explicit active approver from the routing rule, otherwise the assigned
  active team's leader. The assigned analyst may approve only when that exact
  relationship also holds.
- `REQUEST_ACCEPT` is required for REQ acceptance approve and reject; the actor
  must also be CLIENT, belong to the ticket organization, and be either the
  ticket business owner or an explicitly stored delegate for that ticket.
- Ordinary internal edges require TICKETFLOW1, `TICKET_TRANSITION`, and either
  assigned developer membership or membership/leadership of an assigned team.
- Correction and deployment rejection reasons are trimmed plain text of 2–2000
  characters. Client correction reasons are PUBLIC; internal approval and
  deployment reasons are INTERNAL unless the matrix states client-visible.
- `APPROVAL_SUBMIT`, `APPROVAL_APPROVE`, `APPROVAL_REJECT`,
  `ACCEPTANCE_SUBMIT`, `ACCEPTANCE_APPROVE`, `ACCEPTANCE_REJECT`, and
  `CORRECTION_RETURN` are protected operations. They are never advertised in
  `allowedTransitions` or accepted by the generic transition endpoint.
- Responsibility changes only as listed in the matrix. The assigned analysis
  team/developer are preserved into implementation unless a caller holding
  `TICKET_ASSIGN` performs a separately audited reassignment.
