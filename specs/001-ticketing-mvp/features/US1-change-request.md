# US1 — Change Request lifecycle with proposal approval

**Priority**: P1 · **Source**: [spec.md § User Story 1](../spec.md#user-scenarios--testing-mandatory)

## Story

A client contributor submits a request for a service enhancement. A TicketFlow1
ticket lead analyzes it and writes a proposal (scope, estimated delivery). A
client approver reviews the proposal and approves or rejects it. Once
approved, TicketFlow1 develops, tests, and releases the change, and the ticket is
closed.

**Why P1**: This is the feature that makes the tool "process-based" rather
than generic CRUD. The proposal approval gate and the distinction between
Change Requests and Tasks is the single most business-specific requirement
in doc 02. It runs on the seeded default **Change Request** type and its
default workflow.

## Lifecycle (seeded default Change Request workflow)

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED: create (TICKET_CREATE)
    SUBMITTED --> ANALYSIS: start analysis (TICKET_TRANSITION)
    SUBMITTED --> CANCELLED: cancel (TICKET_TRANSITION)
    ANALYSIS --> PROPOSAL: protected proposal-create operation
    ANALYSIS --> CANCELLED: cancel (TICKET_TRANSITION)
    PROPOSAL --> PROPOSAL_APPROVED: protected approve operation (PROPOSAL_APPROVE + CLIENT)
    PROPOSAL --> PROPOSAL_REJECTED: protected reject operation (PROPOSAL_APPROVE + CLIENT)
    PROPOSAL_REJECTED --> ANALYSIS: revise & resubmit (TICKET_TRANSITION)
    PROPOSAL_REJECTED --> CANCELLED: cancel (TICKET_TRANSITION)
    PROPOSAL_APPROVED --> DEVELOPMENT: start development (TICKET_TRANSITION)
    DEVELOPMENT --> FIRST_OCCURRENCE_TESTING: dev complete (TICKET_TRANSITION)
    FIRST_OCCURRENCE_TESTING --> USER_ACCEPTANCE_TESTING: FOT passed (TICKET_TRANSITION)
    USER_ACCEPTANCE_TESTING --> READY_FOR_PRODUCTION: UAT passed (TICKET_TRANSITION)
    READY_FOR_PRODUCTION --> IN_PRODUCTION: deploy (TICKET_TRANSITION)
    IN_PRODUCTION --> CLOSED: close (TICKET_TRANSITION)
    CANCELLED --> [*]
    CLOSED --> [*]
```

Each transition carries a required permission and operation kind. Ordinary lifecycle moves
require `TICKET_TRANSITION`; the proposal decision additionally requires
`PROPOSAL_APPROVE` **and** CLIENT party — the seeded `CLIENT_APPROVER` role
template holds that permission. `currentResponsibility` flips to `CLIENT` on
entering `PROPOSAL` and back to `TICKETFLOW1` on entering `PROPOSAL_APPROVED` or
`PROPOSAL_REJECTED`; `TICKETFLOW1` for every other state. Full diagram + all
three default lifecycles: [plan.md § Workflow engine](../plan.md#workflow-engine).

Protected proposal transitions are never returned in standard
`allowedTransitions` and cannot be invoked through the generic transition API.

## Acceptance scenarios

1. **Given** a client contributor is logged in, **when** they submit a new
   Change Request, **then** the ticket is created with status `SUBMITTED`,
   type `CHANGE_REQUEST`, and `currentResponsibility = TICKETFLOW1`.
2. **Given** a Change Request in `SUBMITTED`, **when** a TicketFlow1 ticket lead
   moves it to `ANALYSIS` then creates a proposal, **then** the ticket status
   becomes `PROPOSAL` and `currentResponsibility` switches to `CLIENT`.
3. **Given** a proposal in `PENDING`, **when** a user holding the
   `PROPOSAL_APPROVE` permission on the client side approves it, **then** the
   proposal status becomes `APPROVED`, the ticket status becomes
   `PROPOSAL_APPROVED` (then `DEVELOPMENT` once TicketFlow1 picks it up), and
   `currentResponsibility` switches back to `TICKETFLOW1`.
4. **Given** a proposal in `PENDING`, **when** a client approver rejects it,
   **then** the proposal status becomes `REJECTED`, the ticket status becomes
   `PROPOSAL_REJECTED`, and the ticket lead can move it back to `ANALYSIS` or
   to `CANCELLED`.
5. **Given** a Change Request not yet at `PROPOSAL_APPROVED`, **when** any
   user attempts to transition it directly to `DEVELOPMENT`, **then** the
   system rejects the transition as invalid for the current state.
6. **Given** a client contributor whose role does not grant `PROPOSAL_APPROVE`,
   **when** they attempt to approve a proposal, **then** the system rejects the
   action — approval requires the `PROPOSAL_APPROVE` permission and CLIENT
   party, never a role name.

**Edge case** (see [spec.md § Edge Cases](../spec.md#edge-cases)): no limit
on rejection/resubmission cycles in MVP — the ticket lead can always attempt
another proposal or cancel.

## Requirements

FR-001, FR-002, FR-003, FR-006, FR-007, FR-011, FR-018 — full text in
[spec.md § Functional Requirements](../spec.md#functional-requirements).

## API

| Endpoint | Contract |
|---|---|
| `POST /api/tickets` (type=CHANGE_REQUEST) | [contracts/tickets.md](../contracts/tickets.md) |
| `POST /api/tickets/{ticketKey}/transition` | [contracts/tickets.md](../contracts/tickets.md) |
| `POST /api/tickets/{ticketKey}/proposals` | [contracts/proposals.md](../contracts/proposals.md) |
| `POST /api/proposals/{proposalId}/approve` | [contracts/proposals.md](../contracts/proposals.md) |
| `POST /api/proposals/{proposalId}/reject` | [contracts/proposals.md](../contracts/proposals.md) |

## Entities

`Ticket`, `ChangeProposal` — field definitions in [data-model.md](../data-model.md).

## Tasks

- Phase 2 (Ticket Core): T023, T024
- Phase 3 (Workflow/Transitions): T028, T029, T030
- Phase 5 (Change Proposals — dedicated to this story): T052–T060
- Phase 7 (Frontend): T083, T084, T086

Full task text: [tasks.md](../tasks.md). Verify gates for this feature: T027
(ticket core), T035 (standard transitions), **T060** (full CR flow end-to-end,
including one rejection-and-resubmission cycle — the definitive check for
this story).

## Success criteria

SC-001 (full lifecycle incl. rejection/resubmission with no manual data
correction), SC-002 (100% of actions audited) — [spec.md § Success Criteria](../spec.md#success-criteria).
