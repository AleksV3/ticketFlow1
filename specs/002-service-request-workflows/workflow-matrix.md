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

