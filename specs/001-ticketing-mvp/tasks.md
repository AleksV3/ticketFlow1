# Tasks: TicketFlow1 Ticketing Tool — MVP

**Input**: Design documents from `specs/001-ticketing-mvp/` (spec.md, plan.md,
research.md, data-model.md, contracts/, quickstart.md)

**Tests**: Backend logic that the constitution marks non-negotiable
(transition validation, permission enforcement, SLA calculation, org isolation)
gets unit/integration tests per research.md's Testing Strategy. Full TDD is not
mandated for every CRUD endpoint — see individual phase notes.

**Organization**: Tasks are grouped by the constitution's build order (backend
foundation + RBAC → configuration model + ticket core → workflow engine →
comments/attachments → change proposals → defect SLA → frontend → polish/demo),
**not** by user story priority. Each user story from spec.md is labeled
(`[US1]`–`[US8]`) wherever a task serves it specifically.

The app runs on **seeded defaults** at every step — the default roles, ticket
types, and workflows are seeded so nothing is a broken half-configured state
(constitution Principle V). Every phase ends with a **Verify** task.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependency on an
  incomplete task in this phase)
- **[Story]**: Which user story (spec.md) this task serves, when applicable

---

## Phase 1: Backend Foundation & RBAC

Goal: a running Spring Boot app with Postgres, JWT login, permission-based
security, the seeded permission catalog + default roles, and a bootstrap admin.

- [x] T001 Create `backend/` Spring Boot 3.x project (Java 21, Maven wrapper) with dependencies: Spring Web, Spring Data JPA, Spring Security, Flyway, PostgreSQL driver, springdoc-openapi, jjwt (plan.md Technical Context)
- [x] T002 [P] Create `frontend/` Next.js (App Router) + TypeScript + Tailwind scaffold — no pages yet beyond the default
- [x] T003 [P] Create root `docker-compose.yml` with a `postgres` service (Postgres 16, named volume, exposed on 5432) and a `.env.example` documenting `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`/`JWT_SECRET`
- [x] T004 Configure `backend/src/main/resources/application.yml`: datasource pointing at the Compose Postgres, `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate` (schema truth lives in Flyway)
- [x] T005 Add Flyway migration `db/migration/V1__create_rbac.sql`: `permission`, `role`, `role_permission`, `organization`, `app_user` tables per data-model.md (bigint identity PKs; `party` as TEXT+CHECK). Seed the permission catalog, the five default **role templates** (`ADMIN`, `CLIENT_USER`, `CLIENT_APPROVER`, `TICKETFLOW1_USER`, `TICKETFLOW1_MANAGER`) with their `role_permission` mappings, a bootstrap `ADMIN` user (BCrypt demo password), and 2 demo Organizations so login can be verified this phase
- [x] T006 [P] Implement the permission-catalog constants and `Permission`, `Role`, `RolePermission` JPA entities + repositories in `backend/src/main/java/com/ticketflow1/ticketing/rbac/`
- [x] T007 [P] Implement `Organization` entity + `OrganizationRepository` in `organization/`
- [x] T008 [P] Implement `AppUser` entity (`party`, `role_id` FK), `AppUserRepository`, and a service-layer check that `organizationId` is required when `party = CLIENT` and null when `party = TICKETFLOW1`, in `user/` (data-model.md validation rules)
- [x] T009 Implement JWT issuing/parsing (`JwtService`) and a `OncePerRequestFilter` that validates `Authorization: Bearer` and populates `SecurityContext` with the user's **permission authorities** (resolved from its role's permissions) plus `party` and `organizationId` claims, in `auth/` (research.md Authentication decision)
- [x] T010 Configure method security so `@PreAuthorize("hasAuthority('<PERMISSION_KEY>')")` gates endpoints, and implement global exception handling (`@RestControllerAdvice`, `ApiExceptionHandler`) producing the standard error shape/codes from contracts/README.md (`VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `INTERNAL_ERROR`; `ILLEGAL_TRANSITION`/`INVALID_STATE` added in Phase 3)
- [x] T011 Implement `POST /api/auth/login` and `GET /api/users/me` in `auth/AuthController.java` per contracts/auth.md (the `me` response includes the caller's permission set for the frontend)
- [x] T012 [P] [US6] Implement `GET/POST/PATCH /api/admin/organizations` in `organization/OrganizationAdminController.java` per contracts/admin.md, gated `hasAuthority('USER_MANAGE')`
- [x] T013 [P] [US6] Implement `GET/POST /api/admin/users` in `user/UserAdminController.java` per contracts/admin.md, gated `USER_MANAGE`, enforcing the party/org rule from T008
- [x] T014 [P] [US7] Implement `GET /api/admin/permissions`, `GET/POST/PATCH /api/admin/roles` in `rbac/RoleAdminController.java` per contracts/admin.md, gated `ROLE_MANAGE` — create/edit a role as a bundle of catalog permissions
- [x] T015 **Verify**: `docker compose up -d postgres`, start backend, confirm Flyway applies `V1`, Swagger UI loads, `POST /api/auth/login` for the bootstrap admin returns a JWT, and a `USER_MANAGE`-gated endpoint returns `403` for a token whose role lacks it

---

## Phase 2: Configuration Model & Ticket Core

Goal: the seeded configurable types/workflows exist as data, and tickets can be
created and read (org-scoped) referencing them. AuditLog/StatusHistory are
introduced here because Principle II requires `TICKET_CREATED` recorded from the
first ticket.

- [x] T016 Add Flyway migration `db/migration/V2__create_workflow_model.sql`: `ticket_type`, `workflow`, `workflow_state`, `workflow_transition` tables per data-model.md (`updated_at`/`updated_by_id` audit columns on every table; each transition carries `required_permission_id`, optional `required_party`, `responsibility_after`). Seed the three default **types** (Change Request, Task, Defect) and their default workflows/states/transitions exactly per plan.md's seeded state diagrams
- [x] T017 [P] Implement `TicketType`, `Workflow`, `WorkflowState`, `WorkflowTransition` entities + repositories in `workflow/`
- [x] T018 Add Flyway migration `db/migration/V3__create_ticket.sql`: `ticket` table (`ticket_type_id`, `current_state_id` FKs; `priority`/`severity`/`current_responsibility` as TEXT+CHECK; `ticket_key` sequence `TF-<n>`; `updated_at`/`updated_by_id` audit columns), plus `status_history` and `audit_log` per data-model.md
- [x] T019 [P] Implement `Ticket` entity + `TicketRepository` with org-scoped query methods (`findByOrganizationId`, …) in `ticket/`
- [x] T020 [P] Implement `AuditLog` entity, repo, and `AuditService.record(...)` (single audit call site) in `audit/`
- [x] T021 [P] Implement `StatusHistory` entity, repo, and `StatusHistoryService.record(...)` in `statushistory/`
- [x] T022 Implement ticket key generation (`TF-<sequence>`) — a Postgres sequence + `TicketKeyGenerator` in `ticket/`
- [x] T023 [US1][US2][US3] Implement `TicketService.createTicket(...)`: resolves the org's `TicketType`, sets `current_state` to that type's workflow initial state, inherits `organizationId` from the business owner, requires `severity` only when the type is Defect (FR-004), writes a `TICKET_CREATED` audit entry + initial `StatusHistory` row (`from_state=null`) in one transaction, in `ticket/TicketService.java`
- [x] T024 [US1][US2][US3] Implement `POST /api/tickets` and `GET /api/tickets/{ticketKey}` in `ticket/TicketController.java` per contracts/tickets.md, with DTOs in `ticket/dto/` — org-scoping enforced (CLIENT-party callers filtered to their own org; cross-org access returns `404`, not `403`)
- [x] T025 [US5] Implement `GET /api/tickets` list with pagination and the filters from contracts/tickets.md (`type`, `status`, `severity`, `priority`, `assignedTo=me`, `responsibility`, `slaStatus`, `organizationId`, `q`) — leave a `// TODO Phase 6` for `slaStatus`, don't stub incorrect behavior
- [x] T026 [P] Unit test `TicketService.createTicket`: correct initial state per type, org inheritance, severity rule, audit + history rows written
- [x] T027 **Verify**: Testcontainers integration test hitting `POST /api/tickets` then `GET /api/tickets/{ticketKey}` for each of the 3 seeded types, confirming the correct initial state and that a CLIENT user from Organization B gets `404` on Organization A's ticket (spec SC-008 partial)

---

## Phase 3: Workflow Engine

Goal: the core differentiator — validated, config-driven, permission-gated
transitions (constitution Principle I).

- [x] T028 [US1][US2][US3] Implement `workflow/TicketTransitionService.transition(ticketKey, toStateKey, actor)`: loads the ticket's type → workflow → the `WorkflowTransition` rows out of the current state; accepts the move only if a matching transition exists **and** the actor holds its `required_permission` **and** matches `required_party` (when set), else throws `IllegalTransitionException` (→ `409 ILLEGAL_TRANSITION`); on success updates `current_state`, applies `responsibility_after`, writes a `StatusHistory` row + `STATUS_CHANGED` audit entry, and optionally stores a `comment` as a `PUBLIC` comment (direct repo call now; refactored to `CommentService` in Phase 4)
- [x] T029 [US1][US2][US3] Implement `POST /api/tickets/{ticketKey}/transition` in `TicketController` per contracts/tickets.md, mapping `IllegalTransitionException` to `409`
- [x] T030 [US1][US2][US3] Add `allowedTransitions` (pre-filtered to the caller's permissions + party) to the `TicketDetail` response — the frontend never computes transition legality itself
- [ ] T031 [P] Implement `GET /api/tickets/{ticketKey}/status-history` in `statushistory/StatusHistoryController.java` per contracts/audit-and-history.md
- [ ] T032 [P] Implement `GET /api/tickets/{ticketKey}/audit-log` in `audit/AuditLogController.java` per contracts/audit-and-history.md
- [ ] T033 [P] Implement `PATCH /api/tickets/{ticketKey}` for non-status field edits (title, description, priority, ticketLead, assignedTeam), each changed field writing its own audit entry (`ASSIGNEE_CHANGED`, `PRIORITY_CHANGED`), rejecting a `status` field in the body with `400`
- [ ] T034 [P] Unit test the engine in `workflow/TicketTransitionServiceTest.java`: for each of the 3 seeded workflows, assert every legal transition succeeds for an actor holding its `required_permission` (+ `required_party`) and fails for an actor lacking the permission or party, and that an undefined transition (e.g. `SUBMITTED → DEVELOPMENT` on a Change Request) is rejected regardless of permission (spec US1 Scenario 5, US2 Scenario 1)
- [ ] T035 **Verify**: `SUBMITTED → ANALYSIS` on a Change Request succeeds for a TICKETFLOW1 user holding `TICKET_TRANSITION` and fails for a CLIENT user; `status-history` and `audit-log` both show the transition

---

## Phase 4: Comments & Attachments

Goal: doc 02's "all documentation and communication in one place."

- [ ] T036 Add Flyway migration `db/migration/V4__create_comment_and_attachment.sql`: `comment` and `attachment` tables per data-model.md, including `updated_at`/`updated_by_id` audit columns
- [ ] T037 [P] [US4] Implement `Comment` entity, repo, `CommentService` (visibility filtering: `INTERNAL` returned only to callers holding the internal-read permission, per FR-012) in `comment/`
- [ ] T038 [P] [US4] Implement `GET/POST /api/tickets/{ticketKey}/comments` in `CommentController` per contracts/comments.md, rejecting `visibility=INTERNAL` from callers lacking `COMMENT_INTERNAL_WRITE` with `403`, writing a `COMMENT_ADDED` audit entry
- [ ] T039 Refactor T028's inline transition-comment persistence to call `CommentService`
- [ ] T040 [P] Implement `Attachment` entity, repo, `AttachmentService` (metadata-only per spec Assumptions) in `attachment/`
- [ ] T041 [P] Implement `GET/POST /api/tickets/{ticketKey}/attachments` per contracts/attachments.md, writing an `ATTACHMENT_ADDED` audit entry
- [ ] T042 [P] [US4] Test: an `INTERNAL` comment is invisible to a CLIENT caller; a caller lacking `COMMENT_INTERNAL_WRITE` posting `visibility=INTERNAL` gets `403`
- [ ] T043 **Verify**: post one `INTERNAL` and one `PUBLIC` comment as a TICKETFLOW1 user, confirm a CLIENT caller's `GET .../comments` returns only the public one; post an attachment, confirm it appears in `GET .../attachments`

---

## Phase 5: Change Proposals

Goal: the Change Request approval gate — spec.md's highest-priority
differentiator (User Story 1).

- [ ] T044 Add Flyway migration `db/migration/V5__create_change_proposal.sql`: `change_proposal` table per data-model.md, including `updated_at`/`updated_by_id` audit columns
- [ ] T045 [US1] Implement `ChangeProposal` entity, repo, `ChangeProposalService` in `proposal/` — `createProposal` rejects tickets whose type has `requires_proposal = false`, tickets not in the proposal-eligible state, or an existing `PENDING` proposal (`409 INVALID_STATE`), and transitions the ticket into `PROPOSAL` (flipping responsibility to CLIENT) via `TicketTransitionService` in the same transaction (no duplicated transition logic)
- [ ] T046 [US1] Implement `approve`/`reject` in `ChangeProposalService`: gated on `PROPOSAL_APPROVE` **and** CLIENT party in the ticket's own org; `reject` requires a non-empty comment; both transition the ticket (`PROPOSAL_APPROVED`/`PROPOSAL_REJECTED`) and write the matching audit entries
- [ ] T047 [US1] Implement `POST /api/tickets/{ticketKey}/proposals`, `POST /api/proposals/{proposalId}/approve`, `POST /api/proposals/{proposalId}/reject` in `proposal/ChangeProposalController.java` per contracts/proposals.md
- [ ] T048 [US1] Extend `TicketDetail` to include the current (most recent) proposal for proposal-enabled types
- [ ] T049 [P] [US1] Unit test: approve/reject succeeds only for `PROPOSAL_APPROVE` + CLIENT party in the ticket's org; a second `PENDING` proposal is blocked; a Task/Defect ticket rejects proposal creation (spec US2 Scenario 2)
- [ ] T050 **Verify**: a Change Request from `SUBMITTED` through `PROPOSAL_APPROVED` including one rejection-and-resubmission cycle, confirming the full audit trail (spec SC-001, SC-002)

---

## Phase 6: Defect SLA

Goal: severity-driven SLA deadlines and status — User Story 3.

- [ ] T051 [US3] Implement `sla/SlaCalculator`: pure function computing `responseDueAt`/`firstInfoDueAt`/`nextUpdateDueAt` from `severity` + `createdAt` per doc 02 §4 formulas
- [ ] T052 [US3] Implement `sla/SlaStatusService.computeStatus(ticket, now)` returning `OK`/`DUE_SOON`/`BREACHED`/`NOT_APPLICABLE` at read time (research.md — not persisted, not a scheduled job)
- [ ] T053 [US3] Wire severity assignment (via the `PATCH` from T033) to call `SlaCalculator` and persist the due-date fields, writing a `SEVERITY_CHANGED` audit entry (old/new), including the recompute-on-downgrade edge case from spec.md
- [ ] T054 [US3] Add the `sla` block (computed `status` + three due dates) to `TicketDetail` for Defect-type tickets; wire the `slaStatus` list filter (deferred TODO from T025) to a repository query against the due-date columns
- [ ] T055 [P] [US3] Unit test `SlaCalculator` for all 4 severities against doc 02 §4; unit test `SlaStatusService` for the `OK`/`DUE_SOON`/`BREACHED` boundaries
- [ ] T056 **Verify**: create a `SEV_1` defect, confirm `responseDueAt = createdAt + 15m` and `slaStatus = OK`; via an integration test with a backdated `createdAt`, confirm `slaStatus` flips to `BREACHED` with no manual recalculation (spec SC-003)

---

## Phase 7: Frontend

Goal: every backend capability becomes usable through a browser, for all five
seeded roles, with the admin configuration surfaces (spec US1–US8 made visible).

- [ ] T057 Implement `frontend/lib/api.ts` (typed fetch client, base URL, `credentials: 'include'`, error-shape parsing per contracts/README.md) and `frontend/lib/auth.ts` (cookie-aware auth helpers + current-user fetch)
- [x] T058 [P] Implement `frontend/app/login/page.tsx` calling `POST /api/auth/login`, relying on the backend's `HttpOnly` auth cookie, redirecting to `/dashboard`
- [ ] T059 [P] Implement shared components in `frontend/components/`: `StatusBadge`, `SlaBadge`, `TransitionButtons` (renders exactly `ticket.allowedTransitions` from the API, never computes legality client-side)
- [ ] T060 [US5] Implement `frontend/app/dashboard/page.tsx` + the backend `dashboard/DashboardService` and `GET /api/dashboard` (contracts/dashboard.md): active/closed counts, by-type, by-status, defects-by-severity, SLA breached/due-soon, waiting-for-approval, waiting-for-confirmation, my-assigned
- [ ] T061 [P] Implement `frontend/app/tickets/page.tsx`: list view with the filters from contracts/tickets.md
- [ ] T062 [P] [US1][US2][US3] Implement `frontend/app/tickets/new/page.tsx`: creation form driven by the org's configured ticket types (severity field only when the chosen type is Defect)
- [ ] T063 [US1][US2][US3][US4] Implement `frontend/app/tickets/[ticketKey]/page.tsx`: detail view — status/type/severity/SLA, business owner/ticket lead, comments (internal toggle for permitted users), attachments, proposal section (proposal-enabled types), audit/history timeline, transition buttons
- [ ] T064 [US6] Implement `frontend/app/admin/users/page.tsx`: list + create user, org + role assignment, calling `GET/POST /api/admin/users`, `GET /api/admin/organizations`, `GET /api/admin/roles`
- [ ] T065 [US7][US8] Implement `frontend/app/admin/config/page.tsx`: manage roles (permission bundles) and ticket types + workflows (states/transitions), calling the `/api/admin/roles`, `/api/admin/ticket-types`, `/api/admin/workflows` endpoints
- [ ] T066 **Verify**: log in as each of the 5 seeded default roles in separate sessions, confirm dashboard/list/detail render without console errors and only permitted transition buttons appear

---

## Phase 8: Polish / Demo

Goal: the tool is demoable, per doc 02 §12/§13.

- [ ] T067 Add Flyway migration `db/migration/V6__seed_demo_data.sql`: 2 Organizations (each with client roles/types cloned from the templates), one user per default role, and sample tickets spanning multiple statuses/severities so the dashboard has real data on first run; seed consistent `updated_at`/`updated_by_id` values
- [ ] T068 [P] Write the `README.md` setup section: prerequisites, `docker compose up`, backend/frontend run commands, seeded demo credentials
- [ ] T069 [P] Write a demo script following doc 02 §12's steps, with the exact seeded accounts for each step
- [ ] T070 **Verify**: time a full demo run end to end — under 10 minutes (spec SC-006) — and run the two-Organization isolation check (spec SC-008) in the same pass

---

## Dependencies

Phases are sequential; each depends on the previous phase's tables/services.

- Phase 1 → Phase 2: tickets attribute creation to an actor and reference the seeded types/roles.
- Phase 2 → Phase 3: transitions operate on tickets and load the workflow rows seeded in Phase 2.
- Phase 3 → Phase 5: proposal approval reuses `TicketTransitionService`.
- Phase 3 → Phase 6: severity change is the `PATCH` from Phase 3; the Defect confirmation transitions come from the Phase 2 seed.
- Phases 1–6 → Phase 7: the frontend has nothing to call before the endpoints exist.
- Phase 4 is independent of Phases 5/6 and can run in parallel if split across two developers.

## Parallel execution notes

- The two developers can split Phase 1 (T002/T003 scaffold vs. T005–T014 backend RBAC), and after Phase 3 lands, split Phase 4 (comments/attachments) against Phase 5 (proposals).
- Within any phase, `[P]` tasks touch different files and are safe to run at once.

## Implementation strategy

**MVP scope** if time runs short before Phase 8: Phases 1–3 plus Phase 6 deliver
the two most distinctive capabilities (validated configurable lifecycles + SLA)
with a working backend. Phase 5 (proposals) and Phase 7 (frontend) are next; the
admin configuration UI (T065) is the lowest-priority screen since the seeded
defaults already make the app fully demoable without it.
