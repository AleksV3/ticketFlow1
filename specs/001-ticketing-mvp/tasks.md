# Tasks: TicketFlow1 Ticketing Tool — MVP

**Last revised**: 2026-07-10 · **Current gate**: Phase 3 completion hardening (T036–T042)

**Input**: Design documents from `specs/001-ticketing-mvp/` (spec.md, plan.md,
research.md, data-model.md, contracts/, quickstart.md)

**Tests**: Backend logic that the constitution marks non-negotiable
(transition validation, permission enforcement, SLA calculation, org isolation)
gets unit/integration tests per research.md's Testing Strategy. Full TDD is not
mandated for every CRUD endpoint — see individual phase notes.

**Organization**: Tasks are grouped by the constitution's build order (backend
foundation + RBAC → configuration model + ticket core → workflow engine →
hardening gate → comments/attachment references → change proposals →
defect SLA/dashboard backend → admin backend/frontend → release/demo),
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
- [x] T003 [P] Create root `docker-compose.yml` with a `postgres` service (Postgres 16, named volume, host port 5433) and a `.env.example` documenting `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`/`JWT_SECRET`
- [x] T004 Configure `backend/src/main/resources/application.yml`: datasource pointing at the Compose Postgres, `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate` (schema truth lives in Flyway)
- [x] T005 Add Flyway migration `db/migration/V1__create_rbac.sql`: `permission`, `role`, `role_permission`, `organization`, `app_user` tables per data-model.md (bigint identity PKs; `party` as TEXT+CHECK). Seed the permission catalog, five default role templates/mappings, and a temporary bootstrap admin for early local verification (removed from deployable profiles by T036/T040)
- [x] T006 [P] Implement the permission-catalog constants and `Permission`, `Role`, `RolePermission` JPA entities + repositories in `backend/src/main/java/com/ticketflow1/ticketing/rbac/`
- [x] T007 [P] Implement `Organization` entity + `OrganizationRepository` in `organization/`
- [x] T008 [P] Implement `AppUser` entity (`party`, `role_id` FK), `AppUserRepository`, and a service-layer check that `organizationId` is required when `party = CLIENT` and null when `party = TICKETFLOW1`, in `user/` (data-model.md validation rules)
- [x] T009 Implement JWT issuing/parsing (`JwtService`) and a `OncePerRequestFilter` that accepts the HttpOnly auth cookie (and Bearer token for non-browser clients) and populates `SecurityContext` with permission, party, and Organization claims
- [x] T010 Configure method security so `@PreAuthorize("hasAuthority('<PERMISSION_KEY>')")` gates endpoints, and implement global exception handling (`@RestControllerAdvice`, `ApiExceptionHandler`) producing the standard error shape/codes from contracts/README.md (`VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `INTERNAL_ERROR`; `ILLEGAL_TRANSITION`/`INVALID_STATE` added in Phase 3)
- [x] T011 Implement login, logout, and current-user endpoints per contracts/auth.md; login sets the HttpOnly auth cookie and `me` includes permissions for UI capability rendering
- [x] T012 [P] [US6] Implement `GET/POST/PATCH /api/admin/organizations` in `organization/OrganizationAdminController.java` per contracts/admin.md, gated `hasAuthority('USER_MANAGE')`
- [x] T013 [P] [US6] Implement `GET/POST /api/admin/users` in `user/UserAdminController.java` per contracts/admin.md, gated `USER_MANAGE`, enforcing the party/org rule from T008
- [x] T014 [P] [US7] Implement `GET /api/admin/permissions`, `GET/POST/PATCH /api/admin/roles` in `rbac/RoleAdminController.java` per contracts/admin.md, gated `ROLE_MANAGE` — create/edit a role as a bundle of catalog permissions
- [x] T015 **Verify**: start Postgres/backend, confirm V1 and Swagger, verify bootstrap login sets the auth cookie, and confirm a `USER_MANAGE` endpoint returns `403` for a caller lacking it

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
- [x] T023 [US1][US2][US3] Implement `TicketService.createTicket(...)`: resolves the Organization's type/initial state; CLIENT callers inherit their Organization while TICKETFLOW1 callers select one; enforce Defect severity and atomically write ticket/audit/initial history
- [x] T024 [US1][US2][US3] Implement `POST /api/tickets` and `GET /api/tickets/{ticketKey}` in `ticket/TicketController.java` per contracts/tickets.md, with DTOs in `ticket/dto/` — org-scoping enforced (CLIENT-party callers filtered to their own org; cross-org access returns `404`, not `403`)
- [x] T025 [US5] Implement `GET /api/tickets` list with pagination and the filters from contracts/tickets.md (`type`, `status`, `severity`, `priority`, `assignedTo=me`, `responsibility`, `slaStatus`, `organizationId`, `q`) — leave a `// TODO Phase 6` for `slaStatus`, don't stub incorrect behavior
- [x] T026 [P] Unit test `TicketService.createTicket`: correct initial state per type, org inheritance, severity rule, audit + history rows written
- [x] T027 **Verify**: Testcontainers integration test hitting `POST /api/tickets` then `GET /api/tickets/{ticketKey}` for each of the 3 seeded types, confirming the correct initial state and that a CLIENT user from Organization B gets `404` on Organization A's ticket (spec SC-008 partial)

---

## Phase 3: Workflow Engine

Goal: the core differentiator — validated, config-driven, permission-gated
transitions (constitution Principle I).

- [x] T028 [US1][US2][US3] Implement `workflow/TicketTransitionService.transition(ticketKey, toStateKey, actor)`: loads the ticket's type → workflow → the `WorkflowTransition` rows out of the current state; accepts the move only if a matching transition exists **and** the actor holds its `required_permission` **and** matches `required_party` (when set), else throws `IllegalTransitionException` (→ `409 ILLEGAL_TRANSITION`); on success updates `current_state`, applies `responsibility_after`, and writes a `StatusHistory` row + `STATUS_CHANGED` audit entry. Until Phase 4 provides `CommentService`, a non-blank transition comment is rejected instead of silently discarded.
- [x] T029 [US1][US2][US3] Implement `POST /api/tickets/{ticketKey}/transition` in `TicketController` per contracts/tickets.md, mapping `IllegalTransitionException` to `409`
- [x] T030 [US1][US2][US3] Add `allowedTransitions` (pre-filtered to the caller's permissions + party) to the `TicketDetail` response — the frontend never computes transition legality itself
- [x] T031 [P] Implement `GET /api/tickets/{ticketKey}/status-history` in `statushistory/StatusHistoryController.java` per contracts/audit-and-history.md
- [x] T032 [P] Implement `GET /api/tickets/{ticketKey}/audit-log` in `audit/AuditLogController.java` per contracts/audit-and-history.md
- [x] T033 [P] Implement `PATCH /api/tickets/{ticketKey}` for non-status field edits (title, description, priority, ticketLead, assignedTeam), each changed field writing its own audit entry (`ASSIGNEE_CHANGED`, `PRIORITY_CHANGED`), rejecting a `status` field in the body with `400`
- [x] T034 [P] Unit test the engine in `workflow/TicketTransitionServiceTest.java`: for each of the 3 seeded workflows, assert every legal transition succeeds for an actor holding its `required_permission` (+ `required_party`) and fails for an actor lacking the permission or party, and that an undefined transition (e.g. `SUBMITTED → DEVELOPMENT` on a Change Request) is rejected regardless of permission (spec US1 Scenario 5, US2 Scenario 1)
- [x] T035 **Verify**: `SUBMITTED → ANALYSIS` on a Change Request succeeds for a TICKETFLOW1 user holding `TICKET_TRANSITION` and fails for a CLIENT user; `status-history` and `audit-log` both show the transition

---

## Phase 3 completion gate: hardening

Goal: close cross-phase correctness/security gaps before comments and proposals
build on the transition engine.

- [ ] T036 Add `V4__phase3_hardening.sql`: optimistic-lock versions for ticket/role/workflow, transition `operation_kind`, `configuration_audit_log`, fixed `COMMENT_INTERNAL_READ` granted to default TicketFlow1 roles, removal of legacy `CONFIG_CHANGED` from ticket audit, and deactivation of the known-password bootstrap user; mark seeded proposal transitions with protected operation kinds
- [ ] T037 Implement optimistic locking for `Ticket` and map stale writes to `409 CONFLICT`; set/clear `closedAt` when entering/leaving terminal states
- [ ] T038 Restrict the generic transition command and `allowedTransitions` to `operationKind=STANDARD`; expose an internal transition method for an owning domain service
- [ ] T039 Implement `ConfigurationAuditService` and record existing organization/role mutations with tenant scope and bounded old/new summaries
- [ ] T040 Harden cookie auth: CSRF cookie/header support, environment-configured CORS origins, `Secure` cookies outside local development, and optional local/demo bootstrap credentials supplied only through environment configuration
- [ ] T041 [P] Test protected-transition rejection, optimistic-lock conflicts, terminal `closedAt`, configuration-audit persistence/scoping, CSRF, and production cookie attributes
- [ ] T042 **Verify**: run the full backend suite and manually confirm a direct generic `ANALYSIS → PROPOSAL` request is rejected without changing ticket/history/audit state

---

## Phase 4: Comments & Attachment References

Goal: public/internal communication with tenant-safe visibility; attachment
records are metadata references only.

- [x] T043 Add `V5__create_comment_and_attachment.sql` with validation constraints and indexes by `(ticket_id, created_at)`
- [x] T044 [P] [US4] Implement `Comment` entity/repository and `CommentService`; resolve the visible parent ticket first and filter INTERNAL reads by `COMMENT_INTERNAL_READ`
- [x] T045 [P] [US4] Implement `GET/POST /api/tickets/{ticketKey}/comments`; enforce public/internal write permissions and write privacy-safe `COMMENT_ADDED` audit entries
- [x] T046 Replace T028's temporary transition-comment rejection with atomic PUBLIC-comment persistence through `CommentService`
- [x] T047 [P] Implement attachment-reference entity/repository/service with filename, MIME, size bounds, and parent-ticket organization scoping
- [x] T048 [P] Implement `GET/POST /api/tickets/{ticketKey}/attachments` and privacy-safe `ATTACHMENT_ADDED` audit entries
- [x] T049 [P] [US4] Test public/internal visibility, cross-org 404s, and comment/attachment validation limits
- [x] T050 [P] [US4] Test privacy-safe comment audit feeds and transactional rollback when audit persistence fails
- [x] T051 **Verify**: compare TicketFlow1 and CLIENT comment/audit responses and confirm no INTERNAL body or event leaks; verify attachment reference isolation

---

## Phase 5: Change Proposals

Goal: proposal creation/decision and its protected workflow transition commit as
one concurrency-safe business command.

- [x] T052 Add `V6__create_change_proposal.sql` with `version` and a partial unique index allowing at most one `PENDING` proposal per ticket
- [x] T053 [US1] Implement `ChangeProposal` entity/repository and deterministic latest query (`createdAt DESC, id DESC`)
- [x] T054 [US1] Implement proposal creation via the protected `PROPOSAL_CREATE` transition in one transaction; reject wrong type/state/party or an existing pending proposal
- [x] T055 [US1] Implement approve/reject via protected operation kinds; require `PROPOSAL_APPROVE` + CLIENT party + same org, store rejection reason as PUBLIC comment, and audit atomically
- [x] T056 [US1] Implement proposal create/approve/reject controllers per contract and map stale decisions to `409 CONFLICT`
- [x] T057 [US1] Extend `TicketDetail` with latest proposal and permitted proposal commands separately from standard `allowedTransitions`
- [x] T058 [P] [US1] Test permissions, party/org isolation, duplicate pending proposals, concurrent decisions, and Task/Defect rejection
- [x] T059 [P] [US1] Test that generic transitions cannot enter/decide proposal states and that failed proposal persistence rolls back ticket/history/audit/comment changes
- [x] T060 **Verify**: complete one rejection/resubmission/approval cycle and inspect the proposal, public reason, ticket audit, and status history

---

## Phase 6: Defect SLA & Dashboard Backend

Goal: event-aware SLA deadlines/status plus a fully tested dashboard API before
frontend work begins.

- [x] T061 [US3] Add `V7__add_defect_sla_events.sql` with `responded_at`, `first_info_at`, and indexes needed by SLA status queries
- [x] T062 [US3] Implement `SlaCalculator` for all four severities, including the documented Europe/Ljubljana weekday approximation
- [x] T063 [US3] Implement `SlaStatusService` with exact `NOT_APPLICABLE`/`BREACHED`/`DUE_SOON`/`OK` precedence and an injectable `Clock`
- [x] T064 [US3] Calculate deadlines at Defect creation and recompute them on audited severity changes
- [x] T065 [US3] Set `respondedAt` on first `REPORTED → ANALYSIS`; set `firstInfoAt` and advance SEV_1/SEV_2 update deadlines from qualifying PUBLIC TicketFlow1 comments
- [x] T066 [US3] Add the complete `sla` response block and database predicates for paginated `slaStatus` filters using the same semantics as `SlaStatusService`
- [ ] T067 [P] [US3] Unit-test formulas, weekday boundaries, warning windows, completed milestones, terminal tickets, and severity recomputation
- [ ] T068 [P] [US3] Integration-test SLA list pagination and status/detail consistency against PostgreSQL
- [ ] T069 [US5] Implement tenant-scoped `DashboardService` and `GET /api/dashboard`; use terminal metadata for counts and document seeded-only waiting cards
- [ ] T070 [P] [US5] Test dashboard counts/lists for empty data, two organizations, active/terminal tickets, assignment, and SLA states
- [ ] T071 **Verify**: backdate SLA milestones without a debug endpoint, confirm each status and dashboard/list/detail agreement

---

## Phase 7: Admin Backend & Frontend

Goal: finish all backend APIs first, then expose every capability through the
browser without duplicating authorization/workflow logic.

- [ ] T072 [US8] Implement tenant-scoped ticket-type/workflow admin services and controllers, additive workflow editing, reference integrity checks, optimistic locking, and configuration audit
- [ ] T073 [P] [US6][US7] Enforce CLIENT admin scope across user/role/type/workflow services; TICKETFLOW1 admins retain cross-org management
- [ ] T074 [P] Implement safe reference endpoints for visible ticket types, active Organizations, active TicketFlow1 leads, and assignable roles
- [ ] T075 [P] Implement paginated `GET /api/admin/configuration-audit` with permission and organization scoping
- [ ] T076 [P] Integration-test all admin mutations, cross-org denial, referenced-state deletion rejection, invalid workflow graphs, and audit entries
- [ ] T077 Implement `frontend/lib/api.ts`: client-side cookie credentials, CSRF header, typed error parsing, and no caching of authenticated reads
- [x] T078 Implement login/logout/current-user flow using the HttpOnly cookie (existing phase-3 scaffold; CSRF wiring completed in T077)
- [ ] T079 Implement application shell, permission-aware navigation, route guards, and accessible loading/error/empty states
- [ ] T080 [P] Implement reusable status/SLA badges, pagination/filter controls, and standard transition buttons driven only by `allowedTransitions`
- [ ] T081 [US5] Implement dashboard UI against the completed dashboard contract
- [ ] T082 [P] Implement ticket list and URL-backed filters
- [ ] T083 [P] [US1][US2][US3] Implement ticket creation using the safe ticket-type reference endpoint
- [ ] T084 Implement ticket detail core and standard field editing/transition flows
- [ ] T085 [P] [US4] Add comments, attachment references, and privacy-safe audit/history sections to ticket detail
- [ ] T086 [P] [US1] Add proposal create/approve/reject UI driven by proposal commands from the API
- [ ] T087 [US6] Implement user administration using assignable-role references
- [ ] T088 [US7] Implement role editor and explain that edits affect the next token issuance
- [ ] T089 [US8] Implement additive ticket-type/workflow editor with protected proposal kinds unavailable for custom workflows
- [ ] T090 [P] Add focused frontend tests for API/CSRF handling, auth redirects, permission navigation, transition rendering, and form validation
- [ ] T091 Add end-to-end smoke coverage for login → ticket creation → transition → comment and proposal approval
- [ ] T092 Verify responsive layout, keyboard operation, labels/focus, and no console errors
- [ ] T093 Verify all five seeded roles see only permitted navigation/actions and both Organizations remain isolated

---

## Phase 8: Release Hardening & Demo

Goal: a clean install is secure, reproducible, and demoable in under ten minutes.

- [ ] T094 Create demo-only Flyway location/profile `db/demo-migration/V8__seed_demo_data.sql`; never include fixed demo credentials in production migrations
- [ ] T095 [P] Complete Docker Compose for Postgres + backend and document frontend startup/environment variables in `.env.example`
- [ ] T096 [P] Replace the placeholder README with prerequisites, profiles, secrets, migration, run, test, and troubleshooting instructions
- [ ] T097 [P] Write the exact seeded-account demo script and expected results for every step
- [ ] T098 Run dependency/security review, verify CSRF/CORS/cookie settings, and confirm bootstrap/demo credentials are disabled outside demo profile
- [ ] T099 Verify a clean database migrates production V1–V7, demo profile applies V8 separately, and backend tests + frontend lint/test/build all pass
- [ ] T100 Verify two-Organization isolation across tickets, comments, proposals, dashboard, admin configuration, and audit endpoints
- [ ] T101 Time the complete 13-step demo under ten minutes and record the result
- [ ] T102 Document basic backup/restore and known MVP limitations

---

## Dependencies and parallel work

- The Phase 3 completion gate blocks Phase 4/5 because both depend on protected transitions, concurrency handling, configuration audit, and security defaults.
- Phase 4 blocks proposal rejection comments and SLA first-info/update event hooks.
- Phase 5 and the pure SLA calculator can otherwise progress independently after Phase 4.
- Dashboard and all admin/reference APIs must be complete before their frontend screens.
- `[P]` means different files and no unfinished schema/service dependency; it does not override the phase gates above.

## Implementation strategy

The core demo path is hardening → comments → proposals → SLA/dashboard →
ticket-facing frontend. If time runs short, defer attachment references and the
custom workflow editor UI before weakening lifecycle validation, audit,
organization isolation, proposal gates, SLA correctness, or security checks.
