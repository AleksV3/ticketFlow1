# US5 — Dashboard overview for prioritizing work

**Priority**: P2 · **Source**: [spec.md § User Story 5](../spec.md#user-scenarios--testing-mandatory)

## Story

A TicketFlow1 manager or ticket lead opens a dashboard showing active vs. closed
ticket counts, tickets by type/status, defects by severity, SLA-breached and
due-soon defects, tickets waiting on client approval or confirmation, and
tickets assigned to them.

**Why P2**: Doc 02 explicitly calls out overviews/dashboards as a core
purpose, and it is the fastest way to demonstrate the tool understands the
business process rather than just storing records. Depends on US1 (waiting
for approval), US3 (SLA cards), and US6 (org-scoped counts) all having data
to aggregate — practically, build it last among the backend-facing stories.

## Acceptance scenarios

1. **Given** at least one `BREACHED` defect exists, **when** the dashboard
   loads, **then** it is listed under an "SLA breached" card.
2. **Given** tickets in `PROPOSAL` status, **when** the dashboard loads,
   **then** they appear under "waiting for client approval".
3. **Given** a logged-in TicketFlow1 user, **when** they view "my assigned
   tickets", **then** only tickets where they are the ticket lead appear.

**Edge case** (see [spec.md § Edge Cases](../spec.md#edge-cases)): with zero
tickets (fresh install), all cards show zero/empty states, no errors.

## Requirements

FR-015 (ticket list filters), FR-016 (dashboard cards, full list) — full
text in [spec.md § Functional Requirements](../spec.md#functional-requirements).

## API

| Endpoint | Contract |
|---|---|
| `GET /api/tickets` (with filters: type, status, severity, priority, assignedTo=me, responsibility, slaStatus, organizationId, q) | [contracts/tickets.md](../contracts/tickets.md) |
| `GET /api/dashboard` | [contracts/dashboard.md](../contracts/dashboard.md) |

Dashboard response shape: `activeCount`, `closedCount`, `byType`,
`byStatus`, `defectsBySeverity`, `slaBreached`, `slaDueSoon`,
`waitingForClientApproval`, `waitingForClientConfirmation`,
`myAssignedTickets` — see [contracts/dashboard.md](../contracts/dashboard.md)
for exact shapes; `slaBreached`/`slaDueSoon` are capped at 20 items each
(link through to the filtered list for the rest).

## Entities

Read-only aggregation over `Ticket` — no new entity of its own.

## Tasks

- Phase 2 (Ticket Core — list/filter endpoint): T025
- Phase 7 (Frontend — dashboard page): T060

Full task text: [tasks.md](../tasks.md). This story has the fewest
dedicated tasks because it's an aggregation view over data every other
story already produces — most of the "work" for US5 is making sure US1/US3/US6
write correct, queryable data, not net-new dashboard logic.

Verify gate: none dedicated — covered by T066 (Phase 7 frontend verify,
log in as different users and confirm dashboard renders) and the
org-isolation check in T070 (Phase 8, confirms a TicketFlow1 manager sees both
orgs' tickets in one dashboard view).

## Success criteria

SC-004: a TicketFlow1 manager can identify every SLA-breached and due-soon defect
from a single dashboard view, without opening individual tickets —
[spec.md § Success Criteria](../spec.md#success-criteria).
