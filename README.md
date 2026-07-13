# TicketFlow1 Ticketing Tool

Internal ticketing system for Change Management and Defect Management — tracks Change Requests, Tasks, and Defects through their lifecycles with roles, comments, audit history, SLA tracking, and dashboards.

Built with Java 21 / Spring Boot / PostgreSQL (backend) and Next.js / TypeScript (frontend).

## Status

Phase 3 of 8 is complete: backend/RBAC foundation, configurable ticket core,
and the validated workflow engine. The current source of truth is
[specs/001-ticketing-mvp/tasks.md](specs/001-ticketing-mvp/tasks.md); phases
4–8 cover comments, proposals, SLA/dashboard, frontend, and release/demo work.

## Quick local setup

1. Copy `.env.example` values into your environment and set a JWT secret of at
   least 32 bytes.
2. Start PostgreSQL with `docker compose up -d postgres`.
3. Run the API with `cd backend && ./mvnw spring-boot:run`.
4. Run the frontend with `cd frontend && npm run dev`.

The API defaults to `http://localhost:8081`; the frontend defaults to
`http://localhost:3000`. See
[quickstart.md](specs/001-ticketing-mvp/quickstart.md) for validation flows and
phase-dependent expectations.

## Verification

- Backend: `cd backend && ./mvnw test` (integration tests require Docker).
- Frontend: `cd frontend && npm run build`.

Demo credentials currently present in early migrations are development-only
scaffolding and must not be used for a production deployment; task T094 moves
all final demo data to a demo-only Flyway profile.

## Documentation source of truth

1. `.specify/memory/constitution.md` — non-negotiable engineering rules.
2. `specs/001-ticketing-mvp/spec.md` — required product behavior.
3. `plan.md`, `research.md`, `data-model.md`, and `contracts/` — technical design.
4. `tasks.md` — implementation order and completion state.
5. Feature pages are navigational overlays; `docs/dashboard.html` is an archived
   tracker for the original task numbering.
