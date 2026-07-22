# TicketFlow1 Presentation Guide

## 30-second explanation

TicketFlow1 is a full-stack, multi-organization service-management
application. It is not just a list of tickets. It enforces business workflows,
permissions, organization isolation, dynamic request forms, team/developer
routing, approvals, comments, attachments, audit history, and SLA deadlines.

The current workflow model supports:

- `TASI` — internal technical service actions.
- `USR` — internal user-service requests.
- `DFCT` — client defect reports with severity and SLA behavior.
- `REQ` — client requests with client acceptance before deployment.

Legacy Change Request, Task, and Defect tickets remain readable after
migration so old history is preserved.

## System structure

```text
User
  |
  v
Next.js frontend
  |
  | REST API
  v
Spring Boot backend
  |
  v
PostgreSQL database
```

- The frontend is the website users interact with.
- The backend is the trusted authority for permissions, workflow rules, tenant
  checks, and validation.
- PostgreSQL stores tickets, users, workflows, subtypes, comments, attachments,
  assignments, and audit history.
- Flyway migrations create and evolve the database schema.

## Main technologies

- Frontend: Next.js, React, TypeScript, Tailwind CSS
- Backend: Java 21, Spring Boot, Spring Security, JPA
- Database: PostgreSQL, Flyway
- Security: JWT in an HttpOnly cookie, CSRF protection, permissions
- Testing: JUnit, Testcontainers, Vitest, Playwright

## The main technical idea

TicketFlow1 treats a ticket as a controlled process. A user cannot move a
ticket to any random status. Before every workflow action, the backend checks:

1. the ticket's current state;
2. whether that transition exists;
3. whether the action is a normal transition or a protected operation;
4. whether the user has the required permission;
5. whether the action belongs to the client or TicketFlow1 side;
6. whether relationship rules are satisfied, such as the REQ business owner
   acceptance rule;
7. whether the ticket belongs to the user's organization.

Frontend buttons are only a convenience. The backend still rejects invalid or
unauthorized API calls.

## Workflow examples

TASI and USR:

```text
NEW
  -> ANALYSIS
  -> PENDING_APPROVAL
  -> IMPLEMENTATION
  -> CLOSED
```

Analysis can return to `NEW` for correction. Approval rejection returns to
`ANALYSIS`, so the team can fix and resubmit.

DFCT:

```text
REPORTED
  -> ANALYSIS
  -> DEVELOPMENT
  -> DEPLOYMENT
  -> CLOSED
```

Deployment can be rejected back to development.

REQ:

```text
SUBMITTED
  -> ANALYSIS
  -> CLIENT_ACCEPTANCE
  -> DEPLOYMENT
  -> CLOSED
```

The client business owner or approved delegate accepts/rejects before
deployment.

## Dynamic forms and routing

TASI and USR require subtypes. Examples:

- TASI: `FIREWALL`, `NETWORK`, `APPLICATION`, `HARDWARE`
- USR: `NEW`, `MODIFY`, `DELETE`

Admins can add, edit, deactivate, and reorder subtypes, fields, options, and
routing rules at runtime. This is stored as configuration data instead of
physical ticket columns. When a ticket is created, the backend validates the
selected subtype fields and resolves the configured team, developer, fallback
developer, and approver.

USR `MODIFY` and `DELETE` require selecting an existing tenant-safe target
user. The ticket stores both the immutable user ID and a display snapshot.

## Security and organization isolation

Client users are locked to their own organization. A client from one company
cannot see another company's tickets, users, comments, attachments, or dynamic
field values.

This is enforced by backend queries and service checks. It is not only hidden
in the frontend.

Authentication uses a signed JWT in an HttpOnly cookie. Mutating browser
requests also require a CSRF token.

## What happens when a ticket is created

1. The backend identifies the logged-in user.
2. It determines the correct organization.
3. It loads the selected ticket type and workflow.
4. It loads the selected subtype and active dynamic fields, if any.
5. It validates required values, field kinds, options, target-user rules, and
   defect severity.
6. It resolves routing to team/developer/approver.
7. It validates parent-ticket scope and cycle rules if a subticket is created.
8. It generates a readable ticket key.
9. It saves the ticket and typed dynamic values.
10. It writes status history and audit entries.
11. It returns the enriched ticket detail to the frontend.

The main coordinator is
[`TicketService.java`](../backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketService.java).

## Recommended live-demo order

1. Log in as `admin@ticketflow1.demo`.
2. Show the dashboard and ticket list.
3. Open admin workflow/type configuration and explain that workflows are data.
4. Open subtype/field/routing configuration for TASI or USR.
5. Create or inspect a TASI/FIREWALL subtype field and routing rule.
6. Create a TASI/FIREWALL ticket and show automatic assignment.
7. Move it to analysis, then use the protected approval panel.
8. Create a USR/MODIFY ticket and show target-user search.
9. Log in as `contributor@alpine.demo` and show that clients only see
   client-allowed ticket types.
10. Show DFCT severity/SLA badges and REQ client acceptance.
11. Finish with comments, attachments, status history, and audit history.

## Strong closing statement

> The main technical achievement is that TicketFlow1 enforces real business
> processes. Ticket types, subtypes, dynamic fields, routing rules, approvals,
> permissions, and audit history are enforced by the backend while client data
> stays isolated by organization.

## Useful answers during questions

### Why use dynamic fields instead of adding database columns?

Because each subtype can need different information. A fixed typed-value model
lets admins change forms without runtime DDL or source-code edits.

### Why are protected actions separate from normal transitions?

Approval and acceptance are business decisions, not just status changes. They
need relationship checks, required reasons, audit entries, and sometimes
comments. Keeping them separate prevents bypassing the approval logic through
the generic transition endpoint.

### Why use type capability for defects?

The system should not depend on one literal ticket key. `DEFECT_SLA` lets both
legacy `DEFECT` and new `DFCT` use the same SLA behavior.

### Why use Flyway migrations?

Migrations make database changes ordered, repeatable, and reviewable. The same
schema can be reproduced in development, tests, and Render.

## Additional project documentation

- [`README.md`](../README.md) explains setup, deployment, and tests.
- [`technical-deep-dive.md`](technical-deep-dive.md) provides deeper
  architecture and implementation detail.
- [`demo-script.md`](demo-script.md) gives a step-by-step presentation flow.
- [`specs/002-service-request-workflows`](../specs/002-service-request-workflows)
  contains the workflow specification, API contracts, and migration record.
