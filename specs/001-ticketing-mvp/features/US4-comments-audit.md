# US4 — Communication and audit trail in one place

**Priority**: P2 · **Source**: [spec.md § User Story 4](../spec.md#user-scenarios--testing-mandatory)

## Story

Any ticket participant adds comments (internal notes visible only to Dinit,
or public notes visible to the client) directly on a ticket. Every status
change, field change, comment, and proposal decision is recorded in an
audit log and a status-history timeline visible on the ticket.

**Why P2**: Doc 02's stated purpose is to "store all documentation and
communication in one place" — this is what makes the tool a system of
record instead of a status tracker. It's a cross-cutting concern exercised
by every other story (US1–US3, US5), which is why it has no dedicated
lifecycle diagram of its own.

## Acceptance scenarios

1. **Given** a Dinit user adds a comment marked `INTERNAL`, **when** a
   client user views the ticket, **then** that comment is not visible to
   them.
2. **Given** any transition, field change, or proposal decision on a
   ticket, **when** the ticket's history is viewed, **then** every such
   event appears in the audit log with actor, action, old/new value, and
   timestamp.

## Requirements

FR-009 (comment visibility split), FR-010 (audit log on every action),
FR-011 (status-history timeline) — full text in
[spec.md § Functional Requirements](../spec.md#functional-requirements).

## API

| Endpoint | Contract |
|---|---|
| `GET /api/tickets/{ticketKey}/comments` | [contracts/comments.md](../contracts/comments.md) |
| `POST /api/tickets/{ticketKey}/comments` | [contracts/comments.md](../contracts/comments.md) |
| `GET /api/tickets/{ticketKey}/attachments` | [contracts/attachments.md](../contracts/attachments.md) |
| `POST /api/tickets/{ticketKey}/attachments` | [contracts/attachments.md](../contracts/attachments.md) |
| `GET /api/tickets/{ticketKey}/audit-log` | [contracts/audit-and-history.md](../contracts/audit-and-history.md) |
| `GET /api/tickets/{ticketKey}/status-history` | [contracts/audit-and-history.md](../contracts/audit-and-history.md) |

Note: audit-log and status-history endpoints have **no internal/public
split** — unlike comments, they're procedural facts visible to anyone who
can view the ticket at all (see [contracts/audit-and-history.md](../contracts/audit-and-history.md)).

## Entities

`Comment`, `Attachment`, `AuditLog`, `StatusHistory` — field definitions in
[data-model.md](../data-model.md). `AuditLog` and `StatusHistory` are
append-only with no update/delete operations exposed anywhere in the API.

## Tasks

- Phase 3 (Workflow/Transitions — audit/history read endpoints): T030, T031
- Phase 4 (Comments & Attachments — dedicated to this story): T035–T042
- Phase 7 (Frontend): T063

Full task text: [tasks.md](../tasks.md). Note `AuditLog`/`StatusHistory`
*entities* are actually introduced earlier, in Phase 2 (T018, T019) — every
service writes to them from the moment tickets can be created, per
constitution Principle II ("Audit Everything"). This story's own phase
(4) is specifically about comments/attachments and their audit entries.

Verify gate: **T042** — post one `INTERNAL` and one `PUBLIC` comment as a
Dinit user, confirm a client user's `GET .../comments` only returns the
public one; post an attachment and confirm it appears.

## Success criteria

SC-002: 100% of status transitions, field changes, comments, and proposal
decisions performed during a session are visible in that ticket's audit
trail immediately after the action —
[spec.md § Success Criteria](../spec.md#success-criteria).
