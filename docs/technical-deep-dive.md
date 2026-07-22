# TicketFlow1 Technical Deep Dive

## Purpose of This Document

This document explains TicketFlow1 in enough depth to present the project,
answer technical questions, and understand how a user action travels through
the complete system.

It covers:

- the business problem;
- the architecture;
- the frontend and backend structure;
- authentication and authorization;
- organization isolation;
- workflows and ticket lifecycles;
- proposals, SLAs, comments, attachments, and audit history;
- the database model;
- API design;
- testing, deployment, and important design decisions.

---

## 1. Executive Summary

TicketFlow1 is a multi-organization ticketing and workflow application. It
started with three default ticket types:

1. **Change Request** — requires analysis and a client-approved proposal before
   development.
2. **Task** — follows a shorter operational workflow without a proposal.
3. **Defect** — tracks severity, SLA deadlines, remediation, and client
   confirmation.

The application also includes:

- permission-based access control;
- strict client-organization isolation;
- configurable roles and workflows;
- runtime configurable ticket subtypes, dynamic fields, options, and routing;
- public and internal comments;
- file attachments;
- proposal approval and rejection;
- defect SLA calculation;
- ticket audit and status history;
- operational dashboards;
- organization, user, role, workflow, and team administration.

The current service-request workflow slice adds four configurable process
families:

1. **TASI** — internal technical service actions such as firewall, network,
   application, and hardware work.
2. **USR** — internal user-service requests for new, modified, or deleted
   users.
3. **DFCT** — client defect reports that use the same `DEFECT_SLA` capability
   as legacy defects.
4. **REQ** — client requests that require client acceptance before deployment.

The key idea is that TicketFlow1 does not treat a ticket as a simple database
record. A ticket is a controlled business process with rules, responsibility,
permissions, and a permanent history.

---

## 2. The Business Problem

A basic ticket application normally allows a user to create a record, edit it,
and choose a status. That is not sufficient when a company needs to prove that
work followed an agreed process.

TicketFlow1 answers questions such as:

- Who is currently responsible for the next action?
- Can this user move this ticket to the requested state?
- Does this change require client approval?
- Has a critical defect missed its response deadline?
- Can this client see a ticket owned by another company?
- Who changed the severity or assignment?
- What was the complete sequence of ticket states?
- Can an administrator adapt a workflow without changing source code?

This makes the project closer to a small service-management platform than a
generic CRUD demonstration.

---

## 3. High-Level Architecture

```text
Browser
  |
  | HTTPS / JSON / multipart requests
  v
Next.js 16 + React 19 frontend
  |
  | REST API under /api
  v
Spring Boot 3.5 backend
  |
  | JPA / SQL transactions
  v
PostgreSQL 16
```

Supporting components:

```text
Flyway       -> version-controls the database schema
Spring Security -> authenticates requests and checks permissions
JWT          -> represents the authenticated session
Docker       -> packages and runs local/deployed services
Swagger UI   -> exposes interactive API documentation
```

### Why use separate frontend and backend applications?

The frontend is responsible for presentation and user interaction. The backend
is the trusted authority for business rules, security, validation, and database
access.

This separation matters because anything in the browser can be manipulated.
Hiding a button does not provide security. The backend must reject an invalid
or unauthorized request even when someone calls the API manually.

---

## 4. Repository Structure

```text
ticketFlow1/
|-- backend/                 Java and Spring Boot API
|   |-- src/main/java/       application source code
|   |-- src/main/resources/  configuration and Flyway migrations
|   `-- src/test/            backend unit and integration tests
|-- frontend/                Next.js application
|   |-- app/                 pages and routes
|   |-- components/          shared visual components
|   |-- lib/                 API, authentication, and shared types
|   |-- test/                frontend component/unit tests
|   `-- e2e/                 Playwright browser tests
|-- specs/                   product specification and API contracts
|-- docs/                    explanatory and presentation documents
|-- docker-compose.yml       local PostgreSQL and backend services
|-- render.yaml              Render deployment blueprint
`-- README.md                setup and operational instructions
```

The project was designed specification-first. Product stories and acceptance
criteria were written before implementation and converted into a
dependency-ordered task plan. This is documented in [`DECISIONS.md`](../DECISIONS.md).

---

## 5. Backend Architecture

The backend is under:

```text
backend/src/main/java/com/ticketflow1/ticketing
```

It is organized by business feature rather than by one global controllers
folder and one global services folder.

Important packages include:

| Package | Responsibility |
|---|---|
| `auth` | Login, logout, JWT creation, JWT validation, current user |
| `rbac` | Roles, permissions, and role administration |
| `organization` | Client organization management |
| `user` | User administration and role assignments |
| `ticket` | Ticket creation, reading, editing, listing, and filtering |
| `workflow` | Workflow definitions, states, transitions, and transition execution |
| `proposal` | Change-proposal creation, approval, and rejection |
| `sla` | Defect SLA deadlines and current SLA status |
| `comment` | Public/internal ticket discussion |
| `attachment` | Attachment metadata, upload, download, and removal |
| `audit` | Permanent ticket change records |
| `statushistory` | Ticket state-transition history |
| `dashboard` | Operational counts and attention queues |
| `team` | Developer-team administration and ticket assignment |
| `configaudit` | Audit records for administrative configuration changes |
| `common` | Shared errors, paging, auditing base class, exception handling |

### Controller, service, repository pattern

A normal backend request passes through three main layers:

```text
Controller -> Service -> Repository -> PostgreSQL
```

#### Controller

The controller receives HTTP input. It reads path parameters, query parameters,
and JSON bodies, then delegates to a service. Controllers also declare broad
permission requirements with `@PreAuthorize`.

#### Service

The service contains the business logic. It checks tenant visibility,
validates state, performs calculations, coordinates multiple repositories, and
writes history records. Mutating methods normally run inside a transaction.

#### Repository

The repository uses Spring Data JPA to load and save entities. Repositories do
not decide business policy; they provide persistence operations to services.

#### DTO

DTO means Data Transfer Object. Request DTOs describe accepted API input, and
response DTOs define what is returned. This prevents the REST API from exposing
database entities directly.

---

## 6. A Complete Request Flow

Suppose a logged-in user opens ticket `TF-100`.

```text
1. Browser calls GET /api/tickets/TF-100.
2. Browser automatically includes the authentication cookie.
3. JwtAuthFilter validates the JWT.
4. Spring Security creates an AuthPrincipal for the request.
5. @PreAuthorize checks that the principal has TICKET_READ.
6. TicketController calls TicketService.getTicket(...).
7. TicketService applies organization visibility rules.
8. TicketRepository loads the ticket and relationships.
9. TicketService calculates current SLA status.
10. TicketTransitionService calculates allowed next states.
11. Proposal details are included when applicable.
12. A TicketDetailResponse is serialized to JSON.
13. React stores the response in component state and renders the page.
```

The response does more than return stored columns. It includes derived
information such as allowed transitions, proposal details, and current SLA
status. This allows the UI to present actions that are relevant to the current
user, while the backend remains responsible for enforcing them.

---

## 7. Authentication

### Login process

The frontend sends credentials to:

```http
POST /api/auth/login
```

The backend:

1. Finds the account by email.
2. Verifies the password using BCrypt.
3. Loads all permissions from all roles assigned to the user.
4. Creates a signed JWT containing the user's identity and authorization
   snapshot.
5. Returns the token in an HttpOnly authentication cookie.
6. Returns safe user information to the frontend.

### Why an HttpOnly cookie?

An HttpOnly cookie cannot be read through normal frontend JavaScript. This
reduces the risk of token theft through a script-injection vulnerability.

The browser sends the cookie automatically when the frontend uses:

```ts
credentials: "include"
```

### Stateless authentication

The backend uses `SessionCreationPolicy.STATELESS`. It does not create a
traditional server-side session for each user. Each request is authenticated
from the signed JWT.

### JWT permission snapshot

Permissions are captured when the JWT is issued. If an administrator changes a
user's roles, the already-issued JWT does not rewrite itself. The updated
permissions take effect after a new token is issued, normally at the next
login. This is an explicit MVP design decision.

### Logout

Logout clears the authentication cookie. Because the backend does not keep a
server-side session, logout is primarily cookie removal.

---

## 8. CSRF and CORS Protection

### CSRF

Cookie authentication requires protection against Cross-Site Request Forgery.
For requests that change data, the frontend reads the `XSRF-TOKEN` cookie and
sends its value in the `X-XSRF-TOKEN` header.

Safe methods such as `GET` do not require this header. Mutating methods such as
`POST`, `PATCH`, `PUT`, and `DELETE` do.

### CORS

During local development, the frontend runs on port `3000` while the API runs
on port `8081`. These are different browser origins. Spring Security therefore
has an explicit CORS configuration that:

- allows configured frontend origins;
- allows the required HTTP methods;
- allows `Content-Type` and `X-XSRF-TOKEN` headers;
- permits credentials so cookies can be sent.

In the Render deployment, the frontend proxies `/api` requests to the backend,
while the backend still has an explicit allowed production origin.

---

## 9. Authorization: Roles and Permissions

TicketFlow1 uses Role-Based Access Control, but the application code checks
permissions rather than hardcoded role names.

```text
User -> one or more Roles -> many Permissions
```

Examples:

| Permission | Meaning |
|---|---|
| `TICKET_READ` | View tickets in the permitted scope |
| `TICKET_CREATE` | Create a ticket |
| `TICKET_UPDATE` | Edit permitted ticket fields |
| `TICKET_ASSIGN` | Assign leads, developers, or teams |
| `TICKET_TRANSITION` | Perform standard lifecycle transitions |
| `PROPOSAL_APPROVE` | Approve or reject a proposal |
| `COMMENT_PUBLIC_WRITE` | Add a public comment |
| `COMMENT_INTERNAL_READ` | View internal comments |
| `USER_MANAGE` | Manage organizations and users |
| `ROLE_MANAGE` | Manage roles and permission bundles |
| `WORKFLOW_MANAGE` | Manage workflows |
| `TYPE_MANAGE` | Manage ticket types |

This design provides flexibility. For example, a company could create a
`SERVICE_MANAGER` role and grant selected permissions without adding a
`SERVICE_MANAGER` condition throughout the source code.

### Two authorization levels

Authorization is deliberately checked twice:

1. **Controller level** — `@PreAuthorize` rejects requests missing the broad
   permission.
2. **Service/domain level** — business rules check organization, party,
   workflow state, field-specific authority, and resource ownership.

The frontend also hides unavailable navigation and actions, but that is a user
experience feature, not the security boundary.

---

## 10. Multi-Organization Isolation

Client organizations are separate tenants inside the same database.

An internal TicketFlow1 user may operate across client organizations when
their permissions allow it. A client user is restricted to the organization
stored in their authenticated principal.

For example, ticket lookup for a client uses both values:

```text
ticketKey + organizationId
```

If the ticket key exists but belongs to a different organization, the client
receives a not-found response. This avoids both unauthorized access and
unnecessary disclosure that another organization's ticket exists.

Tenant checks are applied to tickets and to related data such as comments,
attachments, proposals, history, and dashboard results.

---

## 11. Ticket Domain Model

A ticket contains more than a title and status. Its main information includes:

- internal numeric ID;
- public ticket key such as `TF-100`;
- ticket type;
- current workflow state;
- priority;
- optional Defect severity;
- title and description;
- owning organization;
- business owner or reporter;
- ticket lead;
- assigned developers and teams;
- current responsibility (`CLIENT` or `TICKETFLOW1`);
- creation, update, and closure timestamps;
- SLA deadline and completion-event timestamps.

### Why use a readable ticket key?

The numeric database ID is useful internally, but a readable key is better for
communication, URLs, support conversations, and presentations.

### Why separate editing from transitioning?

Normal field changes use:

```http
PATCH /api/tickets/{ticketKey}
```

Status movement uses:

```http
POST /api/tickets/{ticketKey}/transition
```

This prevents a user from bypassing workflow validation by sending a normal
ticket update with a different status. `TicketService` explicitly rejects a
status supplied through the edit endpoint.

---

## 12. Configurable Workflow Model

A workflow is represented as a directed graph stored in relational tables.

```text
Workflow
  |-- WorkflowState
  |     |-- key
  |     |-- initial?
  |     `-- terminal?
  |
  `-- WorkflowTransition
        |-- from state
        |-- to state
        |-- required permission
        |-- required party
        |-- responsibility after
        `-- operation kind
```

A `TicketType` points to a workflow. Therefore two ticket types can use
different processes without placing every state sequence in Java code.

### Transition validation

When a user requests a transition, `TicketTransitionService`:

1. Loads the ticket using tenant-safe lookup.
2. Finds the destination state inside the ticket's workflow.
3. Finds a transition from the current state to that destination.
4. Confirms that it is a standard transition.
5. Checks the transition's required permission.
6. Checks the required party, if one is configured.
7. Changes the current state.
8. Changes responsibility when configured.
9. Saves the ticket.
10. Writes status history.
11. Writes an audit record.
12. Returns a refreshed detail response.

Invalid movement results in a domain error instead of silently changing the
state.

### Operation kinds

Not every state change is a generic transition. Proposal creation, approval,
and rejection have dedicated operation kinds. Their transitions are owned by
the proposal service and are not returned as normal UI transition choices.

This prevents someone from moving a Change Request into an approved state
without creating or approving the corresponding proposal record.

---

## 13. Default Ticket Lifecycles

### Change Request

```text
SUBMITTED
  |-- CANCELLED
  `-- ANALYSIS
        |-- CANCELLED
        `-- PROPOSAL                client becomes responsible
              |-- PROPOSAL_REJECTED -> ANALYSIS or CANCELLED
              `-- PROPOSAL_APPROVED internal team becomes responsible
                    -> DEVELOPMENT
                    -> FIRST_OCCURRENCE_TESTING
                    -> USER_ACCEPTANCE_TESTING
                    -> READY_FOR_PRODUCTION
                    -> IN_PRODUCTION
                    -> CLOSED
```

The proposal gate is the key business rule. Development cannot start before a
valid proposal has been approved by an authorized client-side user.

### Task

```text
SUBMITTED
  |-- CANCELLED
  `-- ANALYSIS
        |-- CANCELLED
        `-- DEVELOPMENT
              -> FIRST_OCCURRENCE_TESTING
              -> USER_ACCEPTANCE_TESTING
              -> READY_FOR_PRODUCTION
              -> IN_PRODUCTION
              -> CLOSED
```

Tasks deliberately omit proposal states. This demonstrates that the workflow
model is type-specific.

### Defect

```text
REPORTED
  |-- CANCELLED
  `-- ANALYSIS
        |-- CANCELLED
        `-- FIX_IN_PROGRESS
              -> CLIENT_CONFIRMATION
                    |-- CLOSED
                    `-- FIX_IN_PROGRESS
```

At client confirmation, the client can accept the resolution or reject it and
reopen work.

---

## 14. Current Responsibility

Status and responsibility are related but different.

The status explains where the ticket is in the process. Responsibility
explains which party must act next.

Examples:

- A Change Request in `ANALYSIS` is the internal team's responsibility.
- A Change Request in `PROPOSAL` is the client's responsibility.
- A Defect in `FIX_IN_PROGRESS` is the internal team's responsibility.
- A Defect in `CLIENT_CONFIRMATION` is the client's responsibility.

The transition record can specify `responsibilityAfter`, allowing this value to
change as part of the same validated transaction as the status.

---

## 15. Change Proposals

A proposal is a separate business record linked to a Change Request. It is not
just a comment or a status name.

The proposal process is:

```text
Ticket lead creates proposal
  -> proposal becomes PENDING
  -> ticket moves to PROPOSAL
  -> client approver reviews
       |-- APPROVE -> proposal APPROVED, ticket PROPOSAL_APPROVED
       `-- REJECT  -> proposal REJECTED, ticket PROPOSAL_REJECTED
```

Dedicated endpoints are used:

```http
POST /api/tickets/{ticketKey}/proposals
POST /api/proposals/{id}/approve
POST /api/proposals/{id}/reject
```

Approval requires both:

- the `PROPOSAL_APPROVE` permission; and
- client-party membership.

This is stronger than checking for a role named `CLIENT_APPROVER`, because the
role can be renamed or replaced while the business capability remains stable.

---

## 16. Defect Severity and SLA

Only Defects have severity. A Defect must use one of:

```text
SEV_1, SEV_2, SEV_3, SEV_4
```

Higher-severity defects have shorter deadlines. The main tracked obligations
are:

- response deadline;
- first-information deadline;
- next-update deadline.

### SLA events

- Moving from `REPORTED` to `ANALYSIS` records that the initial response
  occurred.
- The first public comment from the internal team records first information.
- Later public internal-team comments advance the next-update deadline.

Internal comments do not satisfy a client-facing communication obligation.

### Computed status

The response exposes one of:

```text
OK
DUE_SOON
BREACHED
NOT_APPLICABLE
```

SLA status is calculated when the ticket or dashboard is read. It is not a
stored flag that can become stale.

Conceptually:

```text
active deadlines + current time -> current SLA status
```

- If an active deadline is in the past, status is `BREACHED`.
- If it is inside the warning window, status is `DUE_SOON`.
- Otherwise it is `OK`.
- Non-Defect tickets return `NOT_APPLICABLE`.

This approach avoids a background process whose only purpose would be to keep
a status column synchronized with time.

---

## 17. Comments

Comments support two visibility levels:

| Visibility | Client can view? | Internal team can view? |
|---|---:|---:|
| `PUBLIC` | Yes | Yes |
| `INTERNAL` | No | Yes |

The backend filters comment results. Internal visibility is not achieved only
with CSS or a hidden frontend section.

Comment creation also participates in SLA logic. A public update from the
internal team may complete or refresh a Defect communication obligation.

Main endpoints:

```http
GET  /api/tickets/{ticketKey}/comments
POST /api/tickets/{ticketKey}/comments
```

---

## 18. Attachments

Attachments support metadata-based creation and multipart upload. Users can
list, upload, download, and remove attachment records when they have the
required access.

Main endpoints:

```http
GET    /api/tickets/{ticketKey}/attachments
POST   /api/tickets/{ticketKey}/attachments
POST   /api/tickets/{ticketKey}/attachments/upload
GET    /api/tickets/{ticketKey}/attachments/{attachmentId}/content
DELETE /api/tickets/{ticketKey}/attachments/{attachmentId}
```

The configured default maximum upload size is 100 MiB. Attachment access still
passes through ticket visibility rules, so knowing an attachment identifier is
not sufficient to access another tenant's data.

For a serious production deployment, attachment content should use a durable
object store and an appropriate backup and malware-scanning strategy.

---

## 19. Audit and Status History

### Ticket audit log

The audit log records business changes such as:

- ticket creation;
- title or description edits;
- priority and severity changes;
- assignment changes;
- workflow status changes.

An audit entry can contain:

- ticket;
- actor;
- action;
- field name;
- old value;
- new value;
- timestamp.

### Status history

Status history is optimized for answering:

- Which state did the ticket leave?
- Which state did it enter?
- Who performed the transition?
- When did it happen?

### Configuration audit

Administrative configuration changes use a separate configuration audit log.
This is necessary because a role or workflow change may not belong to any one
ticket.

Separating these concerns keeps the ticket audit model clear while preserving
administrative accountability.

---

## 20. Dashboard Design

The dashboard converts ticket data into operational queues.

It returns:

- active and closed counts;
- counts grouped by type;
- counts grouped by state;
- Defect counts grouped by severity;
- SLA-breached Defects;
- Defects whose SLA is due soon;
- Change Requests waiting for approval;
- Defects waiting for client confirmation;
- tickets assigned to the current user.

Every dashboard result uses the same organization visibility policy as the
normal ticket list. A client dashboard therefore contains only the client's
own organization data.

Dashboard cards link to filtered ticket-list URLs, connecting high-level
metrics to the underlying work rather than presenting dead statistics.

---

## 21. Ticket Listing and Filtering

The ticket list supports server-side filtering and pagination. Filters include
properties such as:

- ticket type;
- workflow state;
- priority;
- severity;
- SLA status;
- lifecycle state;
- assignment.

Spring Data specifications build database predicates from the provided filter
parameters. Pagination limits how many records are returned at once. The
backend uses a default page size and applies a maximum to prevent clients from
requesting an uncontrolled result set.

Tenant scope is added as a mandatory predicate for client users. It is not an
optional frontend filter.

---

## 22. Frontend Architecture

The frontend is a Next.js App Router application. Its main pages are:

| Route | Purpose |
|---|---|
| `/login` | Authenticate the user |
| `/dashboard` | Operational overview |
| `/tickets` | Filterable ticket list |
| `/tickets/new` | Create a ticket |
| `/tickets/[ticketKey]` | Ticket detail and actions |
| `/teams` | Developer-team management |
| `/admin/organizations` | Organization administration |
| `/admin/users` | User administration |
| `/admin/roles` | Role and permission administration |
| `/admin/workflows` | Workflow and ticket-type administration |

### Shared application shell

`AppShell`:

1. Calls `/api/users/me` to load the current session.
2. Redirects unauthenticated visitors to login.
3. checks any permission required by the current page;
4. filters navigation links by permission and party;
5. renders the shared header, user name, navigation, and logout action.

Again, this filtering improves usability. Backend authorization remains the
security authority.

### Shared API client

`frontend/lib/api.ts` provides common `get`, `post`, `patch`, and `put`
functions. It:

- applies the API base URL;
- sends cookies with `credentials: "include"`;
- disables stale browser caching for API reads;
- sends JSON content types;
- adds the CSRF header to mutating requests;
- converts backend errors into a consistent `ApiError` object;
- preserves field-level validation errors.

Centralizing this behavior avoids repeating security-sensitive fetch settings
in every page.

### Component state

Pages use React state and effects to request data, store loading/error states,
and update the screen after actions. Reusable components provide common status,
severity, and SLA badges, plus shared ticket-detail sections.

---

## 23. API Overview

### Authentication

```http
POST /api/auth/login
POST /api/auth/logout
GET  /api/users/me
```

### Tickets and workflow

```http
GET   /api/tickets
POST  /api/tickets
GET   /api/tickets/{ticketKey}
PATCH /api/tickets/{ticketKey}
POST  /api/tickets/{ticketKey}/transition
```

### Proposals

```http
POST /api/tickets/{ticketKey}/proposals
POST /api/proposals/{id}/approve
POST /api/proposals/{id}/reject
```

### Collaboration and history

```http
GET  /api/tickets/{ticketKey}/comments
POST /api/tickets/{ticketKey}/comments
GET  /api/tickets/{ticketKey}/attachments
POST /api/tickets/{ticketKey}/attachments/upload
GET  /api/tickets/{ticketKey}/audit-log
GET  /api/tickets/{ticketKey}/status-history
```

### Dashboard and references

```http
GET /api/dashboard
GET /api/reference/ticket-types
GET /api/reference/organizations
GET /api/reference/ticket-leads
GET /api/reference/assignable-roles
```

### Administration

```text
/api/admin/organizations
/api/admin/users
/api/admin/roles
/api/admin/permissions
/api/admin/workflows
/api/admin/ticket-types
/api/admin/configuration-audit
```

Swagger UI is available locally at:

```text
http://localhost:8081/swagger-ui.html
```

Detailed request and response contracts are stored in
[`specs/001-ticketing-mvp/contracts`](../specs/001-ticketing-mvp/contracts/README.md).

---

## 24. Error Handling and Validation

The backend uses a central REST exception handler. Instead of leaking Java
stack traces, expected failures are converted into consistent API error bodies.

Typical categories include:

- `400` for invalid input;
- `401` for missing or invalid authentication;
- `403` for authenticated users lacking permission;
- `404` for missing or invisible resources;
- `409` for invalid workflow state or illegal transition.

Validation happens at multiple levels:

- request-shape validation on DTOs;
- service-level business validation;
- workflow transition validation;
- database constraints and foreign keys.

The frontend API client parses these responses and can display both a general
message and field-specific errors.

---

## 25. Database Design

The main relationship groups are:

```text
Organization
  |-- Users
  |-- Client roles
  |-- Workflows
  |-- Ticket types
  `-- Tickets

User <-> Roles <-> Permissions

Workflow
  |-- States
  `-- Transitions

Ticket
  |-- Comments
  |-- Attachments
  |-- Change proposals
  |-- Audit entries
  |-- Status-history entries
  |-- Developers
  `-- Developer teams
```

### Important tables

| Table | Purpose |
|---|---|
| `organization` | Client tenant |
| `app_user` | Login identity and party |
| `role` | Named permission bundle |
| `permission` | Stable application capability |
| `app_user_role` | Many-to-many user/role assignment |
| `role_permission` | Many-to-many role/permission mapping |
| `workflow` | Named process definition |
| `workflow_state` | State belonging to a workflow |
| `workflow_transition` | Allowed directed move and its rules |
| `ticket_type` | Type connected to a workflow |
| `ticket` | Main work record |
| `change_proposal` | Approval record for Change Requests |
| `comment` | Public or internal discussion |
| `attachment` | Ticket file metadata/content reference |
| `audit_log` | General ticket change history |
| `status_history` | Workflow-state history |
| `configuration_audit_log` | Administrative change history |
| `developer_team` | Named internal delivery team |

### Numeric primary keys

The schema uses sequential `bigint` primary keys. For a single-database
internal application, these are simple and efficient. Security does not depend
on IDs being difficult to guess; it depends on correct authorization and
tenant-scoped queries.

### Text values with constraints

Several fixed values are represented as text with database checks instead of
PostgreSQL enum types. A check constraint is easier to evolve through a normal
migration when requirements change.

---

## 26. Flyway and Schema Management

Flyway owns the schema. Migrations are under:

```text
backend/src/main/resources/db/migration
```

They are applied in version order. Broadly, the history is:

| Migration | Main change |
|---|---|
| V1 | Permissions, roles, organizations, users |
| V2 | Workflows, states, transitions, ticket types |
| V3 | Tickets, ticket audit, status history |
| V5 | Comments and attachments |
| V6 | Change proposals and protected proposal transitions |
| V7 | Defect SLA events and admin hardening |
| V9 | Multiple roles per user |
| V10 | Ticket developer assignment |
| V11-V12 | Organization template and internal-organization changes |
| V13 | Developer teams |
| V14-V15 | Team board queue ordering |
| V16 | Ticket subtypes, dynamic field definitions/options, typed field values |
| V17 | Subtype routing rules and workflow decision records |
| V18 | Ticket parent/subtype/routing/approver/target-user context |
| V19 | Ticket-type active/sort/capability metadata |
| V20 | TASI, USR, DFCT, and REQ workflow/type seeds |
| V21 | Service-request subtype backfill and clone-function extension |

Hibernate uses:

```yaml
ddl-auto: validate
```

It verifies that Java entities match the migrated schema but does not create or
alter tables automatically. This makes schema changes explicit and reviewable.

The `demo` Spring profile adds a separate demo-data migration. The default
profile does not create fixed demo users. The T064 rehearsal migrated a
sanitized v15 backup through v21 and confirmed legacy ticket data, comments,
attachments, status history, and audit rows were preserved.

---

## 27. Transactions and Consistency

Business operations that change multiple records use Spring transactions.

For example, a successful status transition may update:

1. the ticket's current state;
2. current responsibility;
3. an SLA event timestamp;
4. status history;
5. audit history.

These changes belong together. If one required database operation fails, the
transaction can roll back instead of leaving a partially updated ticket.

Read-only service methods are marked as read-only transactions where
appropriate. This communicates intent and lets the persistence layer optimize
the operation.

---

## 28. Testing Strategy

### Backend tests

The backend contains focused service tests for areas including:

- ticket creation and editing;
- ticket-controller behavior and tenant isolation;
- workflow transitions;
- comments;
- attachments;
- dashboard calculations;
- SLA calculation and current status.

Unit tests isolate business logic with controlled collaborators. Integration
tests use Spring Boot and Testcontainers to exercise the application against a
real PostgreSQL instance.

Testcontainers is especially valuable because behavior involving SQL,
constraints, joins, and Flyway migrations is verified using the same database
family as production.

### Frontend tests

Vitest and Testing Library cover:

- API-client behavior;
- authentication and login behavior;
- shared UI components and permission-based navigation.

### End-to-end tests

Playwright drives the application through a real browser. This verifies that
frontend routes, API calls, cookies, forms, and visible user flows work
together.

### Verification commands

```bash
cd backend && ./mvnw test
cd frontend && npm test
cd frontend && npm run build
cd frontend && npm run test:e2e
```

Backend integration tests require access to a running Docker daemon.

---

## 29. Local Runtime

Default local ports are:

| Service | Address |
|---|---|
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8081` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| PostgreSQL | `localhost:5433` |

PostgreSQL uses port `5433` on the host to avoid a conflict with a common local
PostgreSQL installation on `5432`. Inside Docker, PostgreSQL still listens on
its standard port.

The normal local flow is:

```bash
docker compose up -d --build postgres backend
cd frontend
npm install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api npm run dev
```

Secrets and environment-specific configuration belong in environment
variables. The JWT secret must not be committed to source control.

---

## 30. Deployment Architecture

`render.yaml` describes three Render resources:

```text
ticketflow1-web  -> Next.js Node service
ticketflow1-api  -> Dockerized Spring Boot service
ticketflow1-db   -> PostgreSQL database
```

The frontend uses `/api` as its public API base and forwards requests to the
backend host. The backend receives database credentials and a generated JWT
secret through environment variables.

The demo deployment activates the `demo` Spring profile, which creates sample
accounts and data. This is convenient for presentation but should not be
confused with production provisioning.

Production hardening would include:

- paid persistent infrastructure;
- real account provisioning and password reset;
- secret rotation;
- durable attachment storage;
- database backups and recovery testing;
- monitoring and alerting;
- rate limiting;
- an explicit token revocation or short-lived refresh strategy;
- malware scanning for uploaded content.

---

## 31. Important Design Decisions

### Configurable workflows instead of one hardcoded status enum

Different organizations and ticket types use different processes. Storing the
workflow graph in tables lets administrators adapt states and transitions.

### Permission checks instead of role-name checks

Roles are business labels that may change. Permissions represent stable
capabilities used by the application.

### Protected business transitions

Proposal states cannot be reached through the generic transition endpoint.
This guarantees that the business record and state transition stay consistent.

### Separate audit types

Ticket changes and administrative configuration changes have different scope,
so they use separate audit models.

### Computed SLA status

Time-dependent status is calculated when read, preventing a stored status from
becoming stale.

### Server-side tenant enforcement

Organization filtering belongs in backend queries and services. Frontend-only
filtering would expose data through direct API calls.

### Specification-first development

User stories, requirements, contracts, data models, and ordered tasks were
defined before the full implementation. This improves traceability between a
business requirement and the code that satisfies it.

---

## 32. Strengths of the Project

The strongest aspects to emphasize during a presentation are:

1. **It models real business processes.** The three ticket types genuinely
   behave differently.
2. **Security is enforced in the backend.** Permissions and tenant boundaries
   are not merely visual controls.
3. **The architecture is configurable.** Workflows and roles are stored as
   data.
4. **Important actions are traceable.** Ticket and configuration changes are
   audited.
5. **SLA behavior is event-aware.** Defect deadlines react to response and
   communication events.
6. **The project is deployable.** It includes Docker and a Render blueprint.
7. **The system is tested at several levels.** Unit, integration, frontend,
   and browser tests cover different risks.

---

## 33. Honest Limitations and Future Improvements

A strong presentation should distinguish a complete MVP from a finished
enterprise product.

Potential future work includes:

- full-text ticket search;
- email and in-app notifications;
- a visual drag-and-drop workflow builder;
- configurable SLA policies per organization;
- richer reporting and exports;
- event-based integrations and webhooks;
- attachment object storage and antivirus scanning;
- refresh tokens and immediate session revocation;
- workflow-definition versioning for tickets already in progress;
- optimistic locking and clearer conflict resolution for concurrent edits;
- observability with structured logs, metrics, and traces;
- accessibility and performance audits across all administrative pages;
- a master-detail ticket interface;
- an assistant for natural-language ticket queries.

Mentioning limitations shows that the system boundaries are understood and
that future work can be prioritized intentionally.

---

## 34. Detailed Live-Demo Script

### Part 1: Establish the product

1. Open the login page.
2. Explain that demo accounts represent different personas.
3. Log in as a client contributor.
4. Show that navigation is based on the user's permissions.

Say:

> This user sees only the features allowed by their permission set, and the
> backend independently verifies those permissions on every request.

### Part 2: Show organization isolation

1. Open the ticket list.
2. Explain that the client sees only tickets for their organization.
3. Mention that this is applied in backend database queries.

Say:

> Organization filtering is mandatory backend scope, not an optional filter in
> the browser.

### Part 3: Show client-facing request choices

1. Open **New ticket**.
2. Log in as a client contributor if needed.
3. Show that the client can create client-facing types such as DFCT and REQ,
   but not internal-only TASI or USR.
4. Create or open a DFCT and point out severity, SLA, owner, and responsibility.

Say:

> The server decides which ticket types the caller can create. The frontend is
> only rendering the authorized options returned by the backend.

### Part 4: Demonstrate runtime subtype configuration

1. Log in as `admin@ticketflow1.demo`.
2. Open the workflow/type administration screens.
3. Open subtype and field configuration for TASI or USR.
4. Show that subtypes, fields, options, active state, and order are data.
5. Open routing configuration and show team/developer/approver selection.

Say:

> This is the main configurability point: the app can change request forms and
> routing without adding ticket-table columns or editing source code.

### Part 5: Create and route a TASI ticket

1. Create a TASI ticket.
2. Select a subtype such as FIREWALL.
3. Fill in any configured dynamic fields.
4. Submit and open the ticket detail.
5. Point out subtype, dynamic values, assigned team/developer, approver, and
   workflow commands.

Say:

> Ticket creation is transactional: the ticket, dynamic values, routing result,
> initial status history, and audit entries are saved together.

### Part 6: Demonstrate protected approval and REQ acceptance

1. Move a TASI or USR ticket through analysis to pending approval.
2. Show that approval/rejection appears in a protected command panel, not as a
   normal status button.
3. Open or create a REQ and show client acceptance/rejection.

Say:

> Protected decisions are business operations. They enforce permissions,
> party, relationship rules, required reasons, responsibility changes, history,
> and audit in one backend transaction.

### Part 7: Demonstrate collaboration and traceability

1. Add a public comment.
2. If using an internal account, add or show an internal comment.
3. Upload an attachment.
4. Show status history.
5. Show the audit log.

Say:

> Status history explains the lifecycle, while the audit log records broader
> business changes such as priority, severity, and assignment.

### Part 8: Finish with migration safety

1. Explain that legacy Change Request, Task, and Defect tickets remain readable.
2. Point to the migration rehearsal record.
3. Explain that rollback uses application rollback or backup restore, not
   destructive Flyway undo.

Say:

> The migration is additive. Existing ticket identity and history are not
> rewritten, which is safer for audit and support.

---

## 35. Likely Technical Questions

### Is the frontend secure because it hides buttons?

No. Hiding buttons is only a usability improvement. Real security is enforced
by Spring Security, service-level permission checks, workflow validation, and
tenant-scoped database access.

### Why not store the JWT in local storage?

An HttpOnly cookie prevents ordinary frontend JavaScript from reading the
token, reducing exposure to token theft through injected scripts. Cookie
authentication then requires CSRF protection, which the project also provides.

### Can a client guess another ticket key?

They can guess a string, but the backend loads client tickets with both the key
and authenticated organization ID. A cross-organization ticket is not returned.

### Can someone skip proposal approval with the transition API?

No. Proposal-related transitions use protected operation kinds and are not
available through the standard transition operation.

### Why use both audit log and status history?

Status history provides a clean lifecycle timeline. Audit history records a
broader set of business changes with old and new values.

### Why use PostgreSQL rather than an in-memory database?

The domain is relational: tickets connect to organizations, users, roles,
workflows, comments, proposals, and history. PostgreSQL provides transactions,
constraints, indexing, and production-grade persistence. Testcontainers lets
integration tests use the same database technology.

### What happens when two administrators edit a workflow?

The workflow model includes a version field for optimistic concurrency
protection. This helps detect stale administrative updates instead of silently
overwriting them.

### Are workflows fully dynamic?

Standard states and transitions are data-driven. Some business concepts remain
intentional domain logic—for example proposal operation kinds and Defect SLA
events. This provides configurability without allowing configuration to bypass
important invariants.

---

## 36. Final Presentation Summary

Use this as the final explanation:

> TicketFlow1 is a full-stack workflow and service-management application. The
> frontend provides an interface for different user personas, while the Spring
> Boot backend enforces authentication, permissions, tenant isolation,
> workflow rules, proposal approval, and SLA behavior. PostgreSQL stores both
> operational data and complete history, with Flyway controlling the schema.
> The central achievement is not simply creating tickets—it is ensuring that
> every ticket follows the correct business process and that every important
> action is authorized, tenant-safe, and traceable.

---

## 37. Recommended Reading Order

To understand the actual implementation after reading this document:

1. [`README.md`](../README.md) — runtime and deployment overview.
2. [`spec.md`](../specs/001-ticketing-mvp/spec.md) — product requirements.
3. [`TicketController.java`](../backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketController.java)
   — ticket API surface.
4. [`TicketService.java`](../backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketService.java)
   — ticket orchestration and tenant rules.
5. [`TicketTransitionService.java`](../backend/src/main/java/com/ticketflow1/ticketing/workflow/TicketTransitionService.java)
   — workflow enforcement.
6. [`SecurityConfig.java`](../backend/src/main/java/com/ticketflow1/ticketing/auth/SecurityConfig.java)
   and [`JwtAuthFilter.java`](../backend/src/main/java/com/ticketflow1/ticketing/auth/JwtAuthFilter.java)
   — security pipeline.
7. [`api.ts`](../frontend/lib/api.ts) and
   [`AppShell.tsx`](../frontend/components/AppShell.tsx) — frontend session and
   API foundations.
8. [`tickets/[ticketKey]/page.tsx`](../frontend/app/tickets/[ticketKey]/page.tsx)
   — complete ticket user experience.
9. [`db/migration`](../backend/src/main/resources/db/migration) — evolution of
   the database model.
10. [`DECISIONS.md`](../DECISIONS.md) — reasons behind the main architecture
    choices.
