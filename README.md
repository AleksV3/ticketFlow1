# TicketFlow1

TicketFlow1 is a full-stack, multi-organization ticketing and service-workflow
application. It combines configurable ticket forms, controlled workflows,
team routing, approvals, notifications, SLAs, permissions, and an auditable
history in one system.

The project is functional and deployable, but it is currently undergoing the
production-readiness work documented in
[`specs/004-production-readiness-hardening`](specs/004-production-readiness-hardening).
It should be treated as a controlled pilot rather than a finished
enterprise-production release.

## What the application does

- Creates and manages tickets for multiple organizations.
- Keeps client organizations isolated from each other.
- Supports internal `TASI` and `USR` service requests.
- Supports client `DFCT` defects and `REQ` requests.
- Preserves legacy Change Request, Task, and Defect records.
- Builds subtype-specific forms from database configuration.
- Routes work to teams, team leads, developers, fallback developers, and
  approvers.
- Enforces workflow transitions, approvals, proposals, client acceptance, and
  correction paths in the backend.
- Tracks defect severity and SLA state.
- Provides searchable ticket lists with configurable columns and metadata
  filters.
- Provides configurable dashboards, internal team boards, comments,
  notifications, following, status history, and audit history.
- Provides administration pages for organizations, users, roles, ticket types,
  subtype forms, routing rules, and workflows.
- Supports light and dark themes.

Navigation and controls are filtered for the current user, but frontend hiding
is not considered authorization. The Spring backend remains authoritative for
permissions, organization scope, workflow rules, and validation.

## Architecture

```text
Browser
  |
  v
Next.js frontend
  |
  | REST API + HttpOnly authentication cookie + CSRF
  v
Spring Boot backend
  |
  v
PostgreSQL
```

The current hosted architecture is:

- Frontend: Vercel
- Backend: Google Cloud Run
- Database: Neon PostgreSQL

Render configuration remains in the repository for historical/alternative
deployment use, but Render is not the current primary deployment target.

## Technology versions

| Component | Version |
|---|---:|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring Framework | 6.2.19 |
| Next.js | 16.2.11 |
| React | 19.2.7 |
| PostgreSQL | 16 |
| Documented Neon PostgreSQL instance | 16.14 |

The Maven parent controls Spring dependency versions. Exact installed frontend
versions are recorded in `frontend/package-lock.json`.

## How the project was developed

The project uses a SpecKit-style workflow:

1. `spec.md` defines user requirements and acceptance criteria.
2. `plan.md` records architecture and implementation decisions.
3. `tasks.md` breaks the plan into small, verifiable tasks and phases.
4. Contracts, threat models, and data-model documents define expected
   boundaries before implementation.
5. Code is implemented phase by phase.
6. Each phase is tested, reviewed, and committed before the next phase starts.

The main specifications are:

- [`specs/001-ticketing-mvp`](specs/001-ticketing-mvp) — original application
  and core ticketing behavior.
- [`specs/002-service-request-workflows`](specs/002-service-request-workflows) —
  TASI, USR, DFCT, REQ, subtype forms, routing, and workflow rules.
- [`specs/003-priority-fixes-and-ux`](specs/003-priority-fixes-and-ux) —
  dashboard, filtering, theme, roles, notifications, and UX improvements.
- [`specs/004-production-readiness-hardening`](specs/004-production-readiness-hardening) —
  authorization, database edit leases, cross-instance behavior, release gates,
  validation, observability, and final security work.

AI was used as a development assistant for specification refinement, code,
tests, debugging, and documentation. Requirements, priorities, visual
acceptance, and phase approval remained human decisions.

## Repository layout

```text
backend/    Spring Boot API, security, domain logic, Flyway migrations, tests
frontend/   Next.js application, React components, browser tests
specs/      Specifications, plans, contracts, threat models, and task lists
docs/       Architecture, deployment, demo, presentation, and operations guides
```

## Local development

### Prerequisites

- Docker Engine with Docker Compose v2
- Java 21
- Node.js 20 or newer
- npm

Backend integration tests also require the current user to have access to the
Docker socket.

### 1. Prepare local configuration

```bash
cp .env.example .env
```

Replace `JWT_SECRET` in `.env` with a random secret of at least 32 bytes:

```bash
openssl rand -base64 48
```

Never commit `.env`, `.env.local`, database credentials, or JWT secrets.

### 2. Start PostgreSQL

```bash
docker compose up -d postgres
```

The default local database is exposed on port `5433` to avoid colliding with a
PostgreSQL installation on port `5432`.

### 3. Start the backend

From a second terminal:

```bash
cd backend
JWT_SECRET='replace-with-your-generated-secret' ./mvnw spring-boot:run
```

The backend starts on <http://localhost:8081>. Its health endpoint is:

```text
http://localhost:8081/api/health
```

OpenAPI and Swagger are disabled by default. For trusted local development,
enable them explicitly with:

```bash
APP_API_DOCS_ENABLED=true \
JWT_SECRET='replace-with-your-generated-secret' \
./mvnw spring-boot:run
```

Swagger is then available at <http://localhost:8081/swagger-ui.html>.

### 4. Start the frontend

Create `frontend/.env.local`:

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api
NEXT_PUBLIC_ENABLE_DEV_LOGS=true
```

Then run:

```bash
cd frontend
npm ci
npm run dev
```

Open <http://localhost:3000/login>.

### Demo profile

For an isolated local demonstration, start the backend with:

```bash
SPRING_PROFILES_ACTIVE=demo \
JWT_SECRET='replace-with-your-generated-secret' \
./mvnw spring-boot:run
```

Demo-only accounts in `db/demo-migration` use password `admin123`. Never enable
the `demo` profile in a real deployment.

The normal migration history also currently contains the V25 public test
scenario. Before a real production launch, its test accounts and seed data must
be removed or disabled through a new reviewed migration, and all related
credentials must be rotated. Do not edit an already applied migration.

## Ticket and workflow model

| Type | Audience | Main behavior |
|---|---|---|
| `TASI` | Internal | Technical service action with required subtype and optional approval |
| `USR` | Internal | User-service request with subtype and target-user rules |
| `DFCT` | Client | Defect lifecycle with severity and SLA behavior |
| `REQ` | Client | Request lifecycle with client-side acceptance where configured |

Examples of internal subtypes include `FIREWALL`, `NETWORK`, `APPLICATION`,
`HARDWARE`, `NEW`, `MODIFY`, and `DELETE`.

Ticket types, subtypes, dynamic fields, role grants, routing rules, workflow
states, and transitions are configuration data. Administrators can change this
configuration without adding a physical database column for every form field.

## Authentication and security

Current security controls include:

- Signed JWT authentication in an HttpOnly cookie.
- CSRF protection for browser mutations.
- Secure-cookie support for HTTPS deployments.
- First-login password change for administrator-created users.
- PostgreSQL-backed login rate limiting.
- Role and fixed-permission checks.
- Party and organization isolation.
- Backend validation of workflow and relationship rules.
- Request correlation IDs in structured backend logs.
- Ticket and configuration audit records.
- Browser security headers, including CSP, frame denial, no-sniff, referrer
  policy, and permissions policy.

Production hardening is not complete. The open work and verification gates are
tracked in
[`specs/004-production-readiness-hardening/tasks.md`](specs/004-production-readiness-hardening/tasks.md).

## Database migrations

Flyway owns the PostgreSQL schema. Hibernate uses `ddl-auto=validate` and does
not create or alter production tables.

Migration files are under:

```text
backend/src/main/resources/db/migration
```

`B40__baseline.sql` is the complete baseline used for a new empty database.
Flyway treats it as the replacement for V1 through V40 on a fresh database.
Existing databases retain their recorded V-file history and apply later
migrations normally. The current migration after the baseline is
`V41__harden_team_authorization.sql`.

The historical `V*.sql` files must remain unchanged because existing local,
test, Neon, and Cloud Run-connected databases store their checksums in
`flyway_schema_history`.

To reset only a disposable local database:

```bash
docker compose down -v
docker compose up -d postgres
```

This permanently deletes the local Docker volume. Never run it against data
that must be retained.

## Tests

Backend tests:

```bash
cd backend
./mvnw test
```

The integration suite uses Testcontainers and requires access to a running
Docker daemon.

Frontend unit tests:

```bash
cd frontend
npm test
```

Production build and TypeScript validation:

```bash
cd frontend
npm run build
```

Browser tests:

```bash
cd frontend
npm run test:e2e
```

The complete release gate is not yet fully green. In particular, the current
`npm run lint` script still uses the removed `next lint` command and is
scheduled for replacement in Phase 5. Do not describe the application as
production-ready until the verification tasks in specification 004 pass.

## Deployment

The current deployment guide is
[`docs/free-internet-deployment.md`](docs/free-internet-deployment.md).

### Backend: Cloud Run

Required production configuration includes:

```text
DATABASE_URL=jdbc:postgresql://<neon-host>/<database>?sslmode=require
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
JWT_SECRET=<long-random-secret>
COOKIE_SECURE=true
APP_CORS_ALLOWED_ORIGINS=https://<vercel-domain>
ATTACHMENT_STORAGE_DIRECTORY=/tmp/ticketflow1-attachments
APP_API_DOCS_ENABLED=false
```

Use Google Secret Manager or another secret store for real credentials.

### Frontend: Vercel

Use `frontend` as the Vercel root directory. The supported API configurations
are:

```text
NEXT_PUBLIC_API_BASE_URL=https://<cloud-run-domain>/api
```

or same-origin `/api` with:

```text
NEXT_PUBLIC_API_BASE_URL=/api
BACKEND_HOST=<cloud-run-hostname>
```

Cloud Run CORS configuration must include the exact Vercel frontend origin.

### Database: Neon

Cloud Run connects to Neon through the PostgreSQL JDBC URL and explicit
username/password variables. Flyway applies pending migrations when the backend
starts.

## Important attachment limitation

Attachment metadata is stored in PostgreSQL, but attachment content is
currently stored on the backend filesystem. Cloud Run's filesystem is
temporary, so attachment files can disappear when an instance restarts or is
replaced.

This was intentionally left unchanged while using limited free cloud storage.
Do not promise durable attachments until an object-storage implementation and
backfill are completed.

## Troubleshooting

- **Docker permission denied:** ensure Docker is running and the current user
  belongs to the `docker` group, then sign out and back in.
- **Flyway checksum mismatch:** identify the changed migration. Never use
  `flyway repair` to hide an unexplained difference.
- **Backend cannot connect locally:** confirm PostgreSQL is available on port
  `5433` and the database values match `.env`.
- **Frontend cannot reach the API:** verify `frontend/.env.local`, restart the
  Next.js process, and inspect the browser network response.
- **403 on a mutation:** confirm the user has the required permission, cookies
  are enabled, the frontend origin is allowed, and the CSRF cookie/header pair
  is present.
- **Login requires a password change:** this is expected for a newly created
  user with a temporary password.
- **Swagger returns 404:** set `APP_API_DOCS_ENABLED=true` only in a trusted
  local environment.

## Documentation

- [`docs/ticketflow1-presentation.pptx`](docs/ticketflow1-presentation.pptx) —
  short Slovenian project presentation.
- [`docs/ticketflow1-presentation-notes.md`](docs/ticketflow1-presentation-notes.md) —
  presenter notes.
- [`docs/demo-script.md`](docs/demo-script.md) — suggested live demo.
- [`docs/complete-codebase-guide.md`](docs/complete-codebase-guide.md) —
  detailed codebase walkthrough.
- [`docs/technical-deep-dive.md`](docs/technical-deep-dive.md) — architecture
  and implementation details.
- [`docs/database-er.md`](docs/database-er.md) — database model.
- [`docs/free-internet-deployment.md`](docs/free-internet-deployment.md) —
  Vercel, Cloud Run, and Neon deployment.
- [`docs/release-verification.md`](docs/release-verification.md) — verification
  evidence and remaining release checks.

## Current known limitations

- Attachment files are not durable on Cloud Run.
- PostgreSQL edit leases and durable cross-instance realtime events are still
  planned in specification 004.
- Authentication authority refresh and the complete CI release gate are still
  planned.
- Health currently uses one compatibility endpoint; separate liveness and
  readiness endpoints are planned.
- Some deployment and deep-dive documents still contain historical Render-era
  details and should be treated as background until they are refreshed.
