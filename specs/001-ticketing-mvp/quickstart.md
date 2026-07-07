# Quickstart: TicketFlow1 Ticketing Tool — MVP

Validation guide once the corresponding implementation phase exists. Not a
description of what's built yet — this is written now so each phase has a
concrete "does it actually work" check to run, per constitution Principle V
(small verified steps).

## Prerequisites

- Docker (Postgres via Compose)
- Java 21, Maven or Gradle wrapper (whichever `backend/` uses)
- Node 20+

> **Default ports**: Postgres publishes on host port **5433** and the backend
> runs on **8081** (not the conventional 5432/8080) to avoid clashing with a
> locally-installed Postgres or another service on 8080. Override with
> `POSTGRES_PORT` / `SERVER_PORT` (see `.env.example`) if you prefer the defaults.

## 1. Start the database

```bash
docker compose up -d postgres
```

Verify: `docker compose ps` shows `postgres` healthy.

## 2. Run backend migrations & start the API

```bash
cd backend
./mvnw spring-boot:run   # or ./gradlew bootRun, per whichever build tool Phase 1 picks
```

Verify: Flyway logs show `V1`..`V6` applied with no errors; Swagger UI loads
at `http://localhost:8081/swagger-ui.html`; demo users from `V6__seed_demo_data.sql`
exist (one per role, at least two Organizations).

## 3. Validate the Change Request flow end-to-end (User Story 1)

```bash
# Login as a client contributor, capture the token
TOKEN=$(curl -s -X POST localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"contributor@clientco.demo","password":"demo"}' | jq -r .token)

# Create a Change Request
curl -s -X POST localhost:8081/api/tickets -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"type":"CHANGE_REQUEST","title":"Add SSO","description":"...","priority":"MEDIUM"}'
```

Expected: `201`, `status: "SUBMITTED"`. Repeat login as `TICKETFLOW1_USER`,
transition to `ANALYSIS`, create a proposal, login as `CLIENT_APPROVER`,
approve it, and confirm the ticket's `status` becomes `PROPOSAL_APPROVED`
and `GET .../audit-log` shows all four actions in order (matches spec
SC-001, SC-002; see [contracts/tickets.md](contracts/tickets.md) and
[contracts/proposals.md](contracts/proposals.md) for exact request shapes).

## 4. Validate Defect SLA (User Story 3)

Create a `DEFECT` ticket with `severity: "SEV_1"` and immediately `GET` it;
confirm `sla.responseDueAt` is `createdAt + 15m` and `sla.status = "OK"`
(matches spec SC-003; formulas in [data-model.md](data-model.md)). To
validate breach detection without waiting 15 real minutes, use a backend
integration test that inserts a ticket with a backdated `createdAt` directly
via the repository and asserts the service-computed `slaStatus` — do not add
a debug endpoint to fake the clock in production code.

## 5. Validate permission enforcement (User Story 6, SC-005)

As a `CLIENT_USER` token (whose role lacks `USER_MANAGE`), attempt
`POST /api/admin/users` — expect `403 FORBIDDEN`. Attempt
`POST /api/proposals/{id}/approve` — expect `403 FORBIDDEN` (approval requires
the `PROPOSAL_APPROVE` permission and CLIENT party, which the default
`CLIENT_USER` role does not hold).

## 6. Validate org isolation (SC-008)

Seed two Organizations with one `CLIENT_USER` each. Login as Org A's user,
`GET /api/tickets/{orgBTicketKey}` — expect `404 NOT_FOUND` (contracts/README.md
org-scoping convention). Login as a `TICKETFLOW1_MANAGER`, confirm both orgs'
tickets appear in `GET /api/dashboard`.

## 7. Frontend smoke test

```bash
cd frontend
npm run dev
```

Open `http://localhost:3000/login`, log in as each seeded demo role in turn
(separate browser profiles or incognito windows, since JWT is stored
client-side per session), and confirm the dashboard and ticket list render
without console errors, and that only role-appropriate transition buttons
appear on a ticket detail page (`allowedTransitions` from
[contracts/tickets.md](contracts/tickets.md) drives this — never
client-computed).

## 8. Full demo script

Once all phases are complete, run doc 02 §12's 13-step demo story end to
end and time it — target under 10 minutes (spec SC-006).
