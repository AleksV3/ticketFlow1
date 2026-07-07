# TicketFlow1 Ticketing Tool — Product Requirements & Build Brief

## Purpose of this document

This document gives a second AI/coding agent the product and technical brief for Gregor's TicketFlow1 summer project.

The project should be treated as a simplified internal ticketing system inspired by Jira, but tailored to the mentor's presentation about TicketFlow1's Change Management and Defect Management processes.

The best positioning is:

> A process-based internal ticketing tool for tracking Change Requests, Tasks, Defects, and optionally Disputes through their lifecycle, with roles, comments, documentation, audit history, SLA tracking, and dashboards.

Do not build a random generic ticket CRUD app. Build around the business workflows described below.

---

## Source context from mentor presentation

The mentor provided a PowerPoint titled **"A new ticketing tool"**.

Extracted key points:

### Main purpose

The tool should:

- track tasks, change requests, defects, and disputes through their lifecycle
- store all documentation and communication in one place
- provide overviews and dashboards of active and closed tickets
- support the lifecycle of:
  - Change Management process
  - Defect Management process

---

# 1. Core product concept

## Ticket types

The system should support at minimum:

```text
CHANGE_REQUEST
TASK
DEFECT
```

Optional later:

```text
DISPUTE
```

Do not merge all ticket types into one generic flow. Each type should have its own lifecycle rules.

---

# 2. Change Management process

## Used for

Change Management is for:

1. **Client requests for enhancements of services and/or additional services**
2. **Tasks** — requests requiring action in the system but not requiring changes in program/source code

This means there is an important business distinction:

- Change Request = may require code/service change and proposal approval
- Task = operational/system action, no source code change required

---

## Change Request lifecycle

From the mentor slides:

```text
REQUEST FORM SUBMITTED TO TICKETFLOW1
→ ANALYSIS
→ PROPOSAL
→ client approves/rejects
→ DEVELOPMENT
→ FIRST OCCURRENCE TESTING
→ USER ACCEPTANCE TESTING
→ READY FOR PRODUCTION
→ IN PRODUCTION
→ TICKET CLOSED
```

Recommended status set:

```text
SUBMITTED
ANALYSIS
PROPOSAL
PROPOSAL_APPROVED
PROPOSAL_REJECTED
DEVELOPMENT
FIRST_OCCURRENCE_TESTING
USER_ACCEPTANCE_TESTING
READY_FOR_PRODUCTION
IN_PRODUCTION
CLOSED
CANCELLED
```

Important behavior:

- Only Change Requests need a proposal approval/rejection step.
- If proposal is rejected, ticket may go back to analysis/proposal or be cancelled/closed.
- The system should record who approved/rejected and when.

---

## Task lifecycle

From the mentor slides:

```text
REQUEST FORM SUBMITTED TO TICKETFLOW1
→ ANALYSIS
→ DEVELOPMENT / ACTION
→ FIRST OCCURRENCE TESTING
→ USER ACCEPTANCE TESTING
→ READY FOR PRODUCTION
→ IN PRODUCTION
```

Recommended shared status set with type-specific allowed transitions:

```text
SUBMITTED
ANALYSIS
DEVELOPMENT
FIRST_OCCURRENCE_TESTING
USER_ACCEPTANCE_TESTING
READY_FOR_PRODUCTION
IN_PRODUCTION
CLOSED
CANCELLED
```

Tasks should skip proposal approval.

---

## Change Management roles

From the mentor slides:

| Role | Description |
|------|-------------|
| Business owner | Client-side person who opened a new request |
| Ticket lead | TicketFlow1-side person responsible for the request throughout its lifecycle |
| MCE team | TicketFlow1 team responsible for monitoring ticket lifecycle, preparing offers, and serving as main contact for status updates and delivery dates |
| Contributors group | Client users with permission to submit requests to TicketFlow1 |
| Approvers group | Client users with authority to approve/reject proposals |

Recommended application roles:

```text
ADMIN
CLIENT_CONTRIBUTOR
CLIENT_APPROVER
TICKETFLOW1_TICKET_LEAD
TICKETFLOW1_MCE
TICKETFLOW1_SERVICE_DESK
VIEWER
```

For MVP, these can be simplified to:

```text
ADMIN
CLIENT_USER
CLIENT_APPROVER
TICKETFLOW1_USER
TICKETFLOW1_MANAGER
```

But do not reduce everything to only ADMIN/USER if avoidable. The roles are part of the business value.

---

# 3. Defect Management process

## Used for

Defect Management is for:

- reporting defects and bugs detected by clients
- tracking incidents, especially Severity Level 1

---

## Defect severity levels

From mentor slides:

| Severity | Description |
|----------|-------------|
| Severity Level 1 | High critical application and/or business process, or significant part of BP, is unavailable and/or entire customer service location affected; no commercially reasonable workaround |
| Severity Level 2 | Critical core application and/or business process partly disturbed; partial workaround exists |
| Severity Level 3 | Non-critical application and/or business process partly disturbed; workaround exists |
| Severity Level 4 | Business process not affected; response time on proper malfunction report next business day |

Recommended severity set:

```text
SEV_1
SEV_2
SEV_3
SEV_4
```

Severity should only be required for `DEFECT` tickets.

---

## Defect lifecycle

From the mentor slide:

```text
REPORT DEFECT
→ ANALYSIS
→ CLIENT CONFIRMATION
→ TICKET CLOSED
```

Recommended practical lifecycle:

```text
REPORTED
ANALYSIS
FIX_IN_PROGRESS
CLIENT_CONFIRMATION
CLOSED
CANCELLED
```

Keep it close to the deck but add `FIX_IN_PROGRESS` if useful for implementation clarity.

---

## Defect Management roles

From mentor slides:

| Role | Description |
|------|-------------|
| Business owner | Client-side person who opened a new defect |
| Ticket lead | TicketFlow1-side person responsible for the defect throughout its lifecycle |
| Service Desk team | TicketFlow1 team responsible for monitoring the ticket lifecycle |

Recommended permissions:

- Client user can report defect and comment.
- TicketFlow1 Service Desk/Ticket Lead can triage and transition.
- Client confirms resolution before close.
- Admin can manage everything.

---

# 4. SLA requirements for defects

The mentor presentation includes an Incident Management SLA table. This is one of the strongest business-specific features. Include at least a simplified version.

## SLA rules from presentation

### Severity 1

- Response time: `< 15 minutes` on telephone error report
- SWAT team setup: `< 15 minutes` after response
- First info report: `< 30 minutes` after SWAT setup
- Regular status reports every `120 minutes`
- Final report after problem resolution
- PIR within `< 48 hours` after problem solving

### Severity 2

- Response time: `< 30 minutes` on telephone error report
- First info report: `< 60 minutes` after proper notification
- Regular status reports every `240 minutes`
- Final report after problem resolution

### Severity 3

- Business day 08:00-17:00 CET: response `< 60 minutes` after receiving proper email notification
- Outside business hours: next business day
- First info report with expected recovery time `< 90 minutes` after proper notification
- Final report after problem resolution

### Severity 4

- Response time: next business day
- User notification `< 8 hours` after problem solved, during business hours

---

## MVP SLA implementation

Do not overbuild a perfect business-calendar system at first. Implement a useful approximation:

For `DEFECT` tickets, calculate:

```text
responseDueAt
firstInfoDueAt
nextUpdateDueAt
slaStatus
```

Recommended `slaStatus`:

```text
OK
DUE_SOON
BREACHED
NOT_APPLICABLE
```

Simplified rules:

```text
SEV_1:
  responseDueAt = createdAt + 15 minutes
  firstInfoDueAt = createdAt + 45 minutes
  nextUpdateDueAt = lastUpdateAt + 120 minutes

SEV_2:
  responseDueAt = createdAt + 30 minutes
  firstInfoDueAt = createdAt + 60 minutes
  nextUpdateDueAt = lastUpdateAt + 240 minutes

SEV_3:
  responseDueAt = createdAt + 60 minutes during business hours, otherwise next business day
  firstInfoDueAt = createdAt + 90 minutes during business hours

SEV_4:
  responseDueAt = next business day
```

Dashboard should show:

- breached SLA tickets
- tickets due soon
- Severity 1 active incidents

---

# 5. Core domain model

Recommended entities:

```text
User
Role
Ticket
Comment
Attachment
AuditLog
StatusHistory
ChangeProposal
SlaEvent / SlaSnapshot
```

## Ticket

```text
id
key / ticketNumber
type: CHANGE_REQUEST | TASK | DEFECT | DISPUTE
status
priority
severity nullable
summary/title
description
businessOwnerId
createdById
ticketLeadId nullable
assignedTeam nullable
currentResponsibility: CLIENT | TICKETFLOW1
createdAt
updatedAt
closedAt nullable
responseDueAt nullable
firstInfoDueAt nullable
nextUpdateDueAt nullable
slaStatus nullable
```

## ChangeProposal

```text
id
ticketId
description
estimatedDeliveryDate nullable
effortEstimate nullable
status: PENDING | APPROVED | REJECTED
createdById
decidedById nullable
decidedAt nullable
createdAt
```

## Comment

```text
id
ticketId
authorId
body
visibility: INTERNAL | PUBLIC
createdAt
updatedAt
```

## Attachment

```text
id
ticketId
uploadedById
fileName
contentType
size
storagePath / url
createdAt
```

For MVP, attachments may be metadata-only if file upload is too much.

## AuditLog

```text
id
ticketId
actorId
action
fieldName nullable
oldValue nullable
newValue nullable
createdAt
```

Examples of audit actions:

```text
TICKET_CREATED
STATUS_CHANGED
ASSIGNEE_CHANGED
COMMENT_ADDED
PROPOSAL_CREATED
PROPOSAL_APPROVED
PROPOSAL_REJECTED
SEVERITY_CHANGED
SLA_BREACHED
```

---

# 6. API design

Assume backend is Java Spring Boot with PostgreSQL.

Recommended endpoints:

```http
POST   /api/auth/login
GET    /api/users/me

GET    /api/tickets
POST   /api/tickets
GET    /api/tickets/{id}
PATCH  /api/tickets/{id}
POST   /api/tickets/{id}/transition

GET    /api/tickets/{id}/comments
POST   /api/tickets/{id}/comments

GET    /api/tickets/{id}/attachments
POST   /api/tickets/{id}/attachments

GET    /api/tickets/{id}/audit-log
GET    /api/tickets/{id}/status-history

POST   /api/tickets/{id}/proposals
POST   /api/proposals/{id}/approve
POST   /api/proposals/{id}/reject

GET    /api/dashboard
GET    /api/admin/users
POST   /api/admin/users
```

## Filters

Ticket listing should support filters:

```http
GET /api/tickets?type=DEFECT
GET /api/tickets?status=ANALYSIS
GET /api/tickets?severity=SEV_1
GET /api/tickets?assignedTo=me
GET /api/tickets?responsibility=CLIENT
GET /api/tickets?slaStatus=BREACHED
GET /api/tickets?q=searchText
```

Filtering is more valuable than lots of random features.

---

# 7. Frontend pages

Assume frontend is Next.js.

Recommended pages:

```text
/login
/dashboard
/tickets
/tickets/new
/tickets/[id]
/admin/users
```

## Dashboard should show

- active tickets
- closed tickets
- tickets by type
- tickets by status
- defects by severity
- SLA breached tickets
- due soon tickets
- tickets waiting for client approval
- tickets waiting for client confirmation
- my assigned tickets

## Ticket detail page should show

- title/description
- ticket type
- current status
- current responsibility: CLIENT or TICKETFLOW1
- business owner
- ticket lead
- severity/SLA if defect
- comments
- attachments/documents
- proposal section if change request
- audit/history timeline
- allowed transition buttons

---

# 8. Recommended tech stack

Backend:

```text
Java 21 or 17
Spring Boot
Spring Web
Spring Data JPA
Spring Security
PostgreSQL
Flyway or Liquibase
OpenAPI/Swagger
JUnit + Testcontainers optional
```

Frontend:

```text
Next.js
TypeScript
Tailwind CSS or simple CSS framework
React Hook Form optional
TanStack Query optional
```

Dev/deployment:

```text
Docker Compose for backend + postgres + frontend optional
.env.example
README with setup steps
seed data/demo users
```

---

# 9. MVP build order

Build in this order. Do not start with UI polish.

## Phase 1 — Backend foundation

1. Create Spring Boot project.
2. Configure PostgreSQL.
3. Add Flyway migrations.
4. Create User/Role model.
5. Seed demo users.
6. Add basic auth/JWT or simple session auth.

## Phase 2 — Ticket core

1. Ticket entity.
2. Ticket creation endpoint.
3. Ticket listing/detail endpoints.
4. Type-specific status enums or validated status transitions.
5. Assign ticket lead.
6. Current responsibility field.

## Phase 3 — Workflow behavior

1. Transition endpoint.
2. Allowed transitions per ticket type.
3. Status history.
4. Audit log.

## Phase 4 — Communication/documentation

1. Comments.
2. Internal/public comment visibility if time allows.
3. Attachment metadata or real file upload.

## Phase 5 — Change proposal

1. Proposal creation for Change Requests.
2. Client approver approval/rejection.
3. Audit log entry.
4. Status transition after decision.

## Phase 6 — Defect SLA

1. Severity field.
2. SLA deadline calculation.
3. SLA status computation.
4. Dashboard cards for breached/due soon defects.

## Phase 7 — Frontend

1. Login.
2. Ticket list with filters.
3. Ticket detail.
4. New ticket form.
5. Comments.
6. Transition buttons.
7. Dashboard.

## Phase 8 — Polish/demo

1. Seed realistic demo data.
2. README.
3. Screenshots.
4. Demo script.
5. Docker Compose.

---

# 10. Non-negotiables for a strong project

If time gets tight, prioritize these over fancy features:

1. Different ticket types
2. Different lifecycles
3. Client vs TicketFlow1 responsibility
4. Role-based actions
5. Comments/communication in one place
6. Audit/history
7. SLA tracking for defects
8. Dashboard/overviews

These map directly to the mentor presentation.

---

# 11. Things to avoid

Avoid:

- building a generic TODO app
- overengineering microservices
- spending too long on frontend styling before backend workflow works
- implementing every Jira feature
- trying to build perfect file storage early
- skipping audit logs
- skipping status transition validation
- pretending all ticket types have the same lifecycle

---

# 12. Suggested demo story

Use this as the final presentation/demo flow:

1. Login as a client contributor.
2. Submit a Change Request.
3. Login as TicketFlow1 ticket lead.
4. Move it to Analysis.
5. Create a Proposal.
6. Login as client approver.
7. Approve the Proposal.
8. TicketFlow1 moves it through Development/FOT/UAT/Ready for Production.
9. Show audit trail and comments.
10. Create a Severity 1 Defect.
11. Show SLA deadlines and dashboard alert.
12. Resolve defect and move to Client Confirmation.
13. Show dashboard overview.

This demonstrates that the app understands the business process, not just CRUD.

---

# 13. Success definition

A successful version is not the biggest possible app. A successful version is:

- runs locally with clear setup
- has backend + database working
- has clean workflow logic
- has role-aware actions
- has visible dashboards
- has comments/audit trail
- has defect severity/SLA logic
- is demoable in under 10 minutes

If the agent is coding, every change should be verified by actual commands/tests where possible.
