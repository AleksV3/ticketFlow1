# TicketFlow1

TicketFlow1 is a multi-organization ticketing application for service-request
workflows. It supports legacy Change Requests, Tasks, and Defects, plus the
new configurable workflow model for TASI, USR, DFCT, and REQ tickets. It
includes runtime subtype forms, automatic routing to teams/developers,
relationship-aware approvals, comments, attachments, audit history, defect
SLAs, dashboards, and a Next.js frontend.

## Public demo on Render

The repository includes [`render.yaml`](render.yaml), which provisions a
Next.js web service, a Dockerized Spring Boot API, and PostgreSQL as one Render
Blueprint.

1. Push the branch you want to deploy to GitHub.
2. In Render, choose **New > Blueprint** and connect this repository.
3. Keep the Blueprint name and service settings, then choose **Deploy
   Blueprint**.
4. Open `https://ticketflow1-web.onrender.com` after both services finish.

The Blueprint deliberately uses the `demo` Spring profile. Every demo account
uses password `admin123`; useful starting accounts are:

| Persona | Email |
|---|---|
| Client contributor | `contributor@alpine.demo` |
| Ticket agent | `agent@ticketflow1.demo` |
| Administrator | `admin@ticketflow1.demo` |

The free API sleeps when idle, so the first request can take about a minute.
Render's free PostgreSQL database expires after 30 days and must not be used for
important data. Before using this as a real production service, switch the API
to the `default` profile, use paid persistent infrastructure, provision real
accounts, and configure a durable attachment store.

## Prerequisites

- Docker Engine with Docker Compose v2
- Java 21 for running the backend outside Docker
- Node.js 20+ and npm for the frontend

## Configuration and secrets

Copy the example environment file before starting services:

```bash
cp .env.example .env
```

Replace `JWT_SECRET` with at least 32 random bytes. For example:

```bash
openssl rand -base64 48
```

Never commit `.env`, production database passwords, or JWT secrets. The normal
profile contains no user accounts or fixed credentials.

The supported Spring profiles are:

- `default`: production migrations only; no demo users or sample records.
- `demo`: adds the isolated V8 demo migration with fixed local-only accounts.

## Workflow model

The current configurable workflow slice adds four service-request types:

| Type | Who creates it | Main approval point |
|---|---|---|
| `TASI` | TicketFlow1/internal users | Team lead or configured approver approves implementation |
| `USR` | TicketFlow1/internal users | Team lead or configured approver approves user changes |
| `DFCT` | Client users | TicketFlow1 handles analysis/development/deployment and keeps defect SLA capability |
| `REQ` | Client users | Client business owner or explicit delegate accepts before deployment |

TASI and USR require a subtype such as `FIREWALL`, `NETWORK`, `APPLICATION`,
`HARDWARE`, `NEW`, `MODIFY`, or `DELETE`. Admins can add/edit/deactivate
subtypes, dynamic fields, options, routing rules, and type availability without
adding physical ticket columns or editing source code. Existing legacy tickets
remain readable on their original workflow after migration.

## Run with Docker and local frontend

Start PostgreSQL and the backend:

```bash
docker compose up -d --build postgres backend
```

Then start the frontend:

```bash
cd frontend
cp ../.env.example .env.reference
npm install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api npm run dev
```

Open <http://localhost:3000/login>. The API and Swagger UI run on
<http://localhost:8081> and <http://localhost:8081/swagger-ui.html>.

For demo data, set `SPRING_PROFILES_ACTIVE=demo` in `.env` before the first
start. Demo credentials and the presentation walkthrough are documented in
[`docs/demo-script.md`](docs/demo-script.md).

## Run entirely from source

Start only PostgreSQL:

```bash
docker compose up -d postgres
```

Start the backend:

```bash
cd backend
JWT_SECRET='replace-with-32-or-more-random-bytes' ./mvnw spring-boot:run
```

In another terminal, start the frontend:

```bash
cd frontend
npm install
npm run dev
```

The defaults use PostgreSQL port `5433`, API port `8081`, and frontend port
`3000`. Override them through `.env` and `frontend/.env.local`.

## Database migrations

Flyway owns the schema; Hibernate validates it and never creates tables.
Production migrations are under `backend/src/main/resources/db/migration`.
Demo V8 is under `db/demo-migration` and is added only by the `demo` profile.
The service-request workflow migrations are additive: V16-V21 add subtype
forms, routing, parent-ticket metadata, type activation/capability, and the
TASI/USR/DFCT/REQ workflow seeds. The migration rehearsal and rollback plan are
documented in
[`specs/002-service-request-workflows/migration-strategy.md`](specs/002-service-request-workflows/migration-strategy.md).

Do not edit an applied migration on a persistent environment. This project
removed an early development-only credential from V1 during release hardening,
so databases created before that change must be rebuilt once:

```bash
docker compose down -v
docker compose up -d postgres
```

This deletes local container data. Do not use it against an environment whose
data must be retained.

## Tests and release checks

```bash
cd backend && ./mvnw test
cd frontend && npm test
cd frontend && npm run build
cd frontend && npm run test:e2e
```

Backend integration tests use Testcontainers and therefore require access to a
running Docker daemon. Release verification and tenant-isolation coverage are
described in `docs/release-verification.md`.

## Troubleshooting

- **Flyway checksum mismatch:** rebuild the disposable local database as shown
  above. Never use `flyway repair` to conceal an unexplained production drift.
- **Port already in use:** change `POSTGRES_PORT`, `SERVER_PORT`, or the
  frontend dev port and keep `NEXT_PUBLIC_API_BASE_URL` aligned.
- **401 after startup:** the default profile intentionally has no accounts.
  Use an operationally provisioned account or recreate a local database with
  the demo profile.
- **403 on browser mutations:** confirm the frontend and API origins match the
  configured CORS origin and that cookies are enabled; the client sends the
  CSRF cookie value as `X-XSRF-TOKEN`.
- **Integration tests cannot find Docker:** start Docker and ensure the current
  user can access its socket.
- **Frontend cannot reach the API:** verify `NEXT_PUBLIC_API_BASE_URL` and open
  the browser network panel for CORS or cookie errors.

## Additional documentation

- [`docs/demo-script.md`](docs/demo-script.md) — presentation walkthrough for the service-request workflows
- [`docs/presentation-guide.md`](docs/presentation-guide.md) — concise explanation for presenting the project
- [`docs/technical-deep-dive.md`](docs/technical-deep-dive.md) — deeper architecture and code walkthrough
- [`specs/002-service-request-workflows/contracts`](specs/002-service-request-workflows/contracts) — service workflow API contracts
- [`specs/001-ticketing-mvp/tasks.md`](specs/001-ticketing-mvp/tasks.md) — task source of truth
- [`specs/001-ticketing-mvp/quickstart.md`](specs/001-ticketing-mvp/quickstart.md) — validation flows
- [`docs/database-er.md`](docs/database-er.md) — database model
- [`docs/02-product-requirements-and-build-brief.md`](docs/02-product-requirements-and-build-brief.md) — product brief
