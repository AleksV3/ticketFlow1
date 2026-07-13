# Change Proposals

Only exist for tickets whose type has `requiresProposal = true` — the seeded
`CHANGE_REQUEST` type (FR-003). Proposal status is a fixed set: `PENDING`,
`APPROVED`, `REJECTED`.

The three proposal-related workflow transitions are protected operation kinds.
They cannot be called through the generic ticket-transition endpoint; only
`ChangeProposalService` may invoke them while persisting the proposal mutation
in the same transaction.

## `POST /api/tickets/{ticketKey}/proposals`

Requires `TICKET_TRANSITION` and TICKETFLOW1 party — creating the proposal
fires the `ANALYSIS → PROPOSAL` transition (annotated `TICKET_TRANSITION
[TICKETFLOW1]` in plan.md). Ticket must be in status `ANALYSIS`; the move to
`PROPOSAL` happens automatically as part of creating the proposal.

**Request**:

```json
{
  "description": "Implement SAML SSO via Okta integration, ~3 sprints.",
  "estimatedDeliveryDate": "2026-08-15",
  "effortEstimate": "15 person-days"
}
```

**Response `201`**:

```json
{
  "id": 205,
  "ticketId": 1050,
  "ticketKey": "TF-1050",
  "description": "Implement SAML SSO via Okta integration, ~3 sprints.",
  "estimatedDeliveryDate": "2026-08-15",
  "effortEstimate": "15 person-days",
  "status": "PENDING",
  "createdBy": { "id": 58, "displayName": "Alex TicketFlow1" },
  "createdAt": "2026-07-02T09:00:00Z"
}
```

Writes a `PROPOSAL_CREATED` audit log entry; ticket status becomes
`PROPOSAL`, `currentResponsibility` switches to `CLIENT`.

**Errors**: `409 INVALID_STATE` if the ticket's type does not require a
proposal or the ticket isn't in `ANALYSIS` status, or already has a `PENDING`
proposal.

## `POST /api/proposals/{proposalId}/approve`

Requires `PROPOSAL_APPROVE` and CLIENT party, and the caller must belong to
the same Organization as the ticket (enforced via the ticket's
`organizationId`, not trusted from the request) — FR-011.

**Request**: empty body, or `{ "comment": "Approved, please proceed." }`.

**Response `200`**: updated `ChangeProposal` (`status=APPROVED`,
`decidedBy`, `decidedAt` set). Writes `PROPOSAL_APPROVED` audit log entry;
ticket status becomes `PROPOSAL_APPROVED`, `currentResponsibility` switches
to `TICKETFLOW1`.

**Errors**: `409 INVALID_STATE` if proposal isn't `PENDING`.
`409 CONFLICT` is returned if another decision wins a concurrent race.

## `POST /api/proposals/{proposalId}/reject`

Requires `PROPOSAL_APPROVE` and CLIENT party, same org constraint as approve.

**Request**:

```json
{ "comment": "Scope too broad, please re-estimate a phased approach." }
```

`comment` required on reject (not optional like approve) — a rejection
without a reason is not useful to the ticket lead. The reason is stored as a
PUBLIC comment in the same transaction.

**Response `200`**: updated `ChangeProposal` (`status=REJECTED`). Writes
`PROPOSAL_REJECTED` audit log entry; ticket status becomes
`PROPOSAL_REJECTED`. From there the ticket lead can transition back to
`ANALYSIS` (to draft a new proposal) or to `CANCELLED`.
