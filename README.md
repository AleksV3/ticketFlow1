# TicketFlow1

TicketFlow1 is a multi-organization ticketing application for Change Requests,
Tasks, and Defects. It includes configurable workflows and roles, comments,
audit history, change-proposal approval, defect SLAs, dashboards, and a Next.js
frontend.

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
For plain-HTTP local development, also set `COOKIE_SECURE=false`; production
must retain the secure default.

## Database migrations

Flyway owns the schema; Hibernate validates it and never creates tables.
Production migrations are under `backend/src/main/resources/db/migration`.
Demo V8 is under `db/demo-migration` and is added only by the `demo` profile.

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

- [`specs/001-ticketing-mvp/tasks.md`](specs/001-ticketing-mvp/tasks.md) — task source of truth
- [`specs/001-ticketing-mvp/quickstart.md`](specs/001-ticketing-mvp/quickstart.md) — validation flows
- [`docs/database-er.md`](docs/database-er.md) — database model
- [`docs/02-product-requirements-and-build-brief.md`](docs/02-product-requirements-and-build-brief.md) — product brief
