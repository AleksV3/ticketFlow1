# Release verification

This document records the release checks for the service workflow work in
`specs/002-service-request-workflows`.

## 2026-07-22 verification log

### T063 automated suites

Status: pass.

Executed locally before the release documentation pass:

- Backend workflow and controller suites.
- Frontend unit coverage for dynamic workflow UI.
- End-to-end workflow coverage for TASI routing and approval, USR MODIFY target
  user search, DFCT correction loops, REQ client acceptance, and subticket
  creation.

See the task list for the committed verification point:
`f425ea9 Verify service workflow release suites`.

### T064 migration rehearsal

Status: pass.

The migration was rehearsed against disposable PostgreSQL databases:

- `tf1_t064_base_20260722`
- `tf1_t064_restore_20260722`

The rehearsal migrated a sanitized current-schema backup through the new service
workflow migrations, verified the preserved sample ticket `TF-9001`, and
documented rollback/recovery notes in
`specs/002-service-request-workflows/migration-strategy.md`.

Committed as `77a3c09 Document service workflow migration rehearsal`.

### T065 documentation and contracts

Status: pass.

Updated the README, presentation guide, technical deep dive, API contract notes,
and demo script to describe:

- configurable ticket types and subtypes;
- dynamic fields and options;
- routing rules;
- approval and client acceptance commands;
- parent/child tickets;
- migration and demo instructions.

Committed as `08bac3b Update service workflow documentation`.

### T066 demo data

Status: pass.

Added a demo-only Flyway migration at:

`backend/src/main/resources/db/demo-migration/V22__seed_service_workflow_demo.sql`

The demo seed creates a representative team, dynamic fields, routing rules, and
four demo tickets:

- `TF-2100` TASI/FIREWALL awaiting manager approval.
- `TF-2101` TASI/NETWORK subticket under `TF-2100`.
- `TF-2102` REQ in client acceptance.
- `TF-2103` USR/MODIFY with a target user.

The demo profile was verified against a disposable PostgreSQL database with
Hibernate validation and Flyway migration success.

Committed as `0a07782 Seed service workflow demo data`.

### T067 release/security review

Status: reviewed with follow-up items.

#### Audit and log privacy

Reviewed code paths:

- ticket audit log: `AuditService`
- ticket comments: `CommentService`
- configuration audit: `ConfigurationAuditService`
- dynamic ticket values: `TicketService`
- global exception logging: `ApiExceptionHandler`

Findings:

- Internal comments are only returned to TicketFlow1 users with
  `COMMENT_INTERNAL_READ`.
- Internal comment audit rows are hidden from users without
  `COMMENT_INTERNAL_READ`.
- Dynamic field values are visibility-filtered in ticket details. Client users
  do not receive fields marked `INTERNAL`.
- Dynamic field audit entries record only the fact that values were captured.
  They do not store raw dynamic field values.
- Configuration audit stores bounded metadata such as keys, versions, IDs, and
  active flags.
- Unhandled exceptions are logged with method/path and stack trace. Request
  bodies are not logged by the application code reviewed here.

#### Dependency and security checks

Commands run:

```bash
cd frontend
npm audit --omit=dev --audit-level=high

cd backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
  ./mvnw -q -DskipTests dependency:tree
```

Results:

- Frontend production audit reported three advisories:
  - `postcss` moderate advisory via the `next` dependency tree.
  - `sharp` high advisories via the `next` dependency tree.
- The automated `npm audit fix --force` recommendation is not safe to apply as
  a blind release step because it proposes a breaking dependency change. Treat
  this as a dependency-upgrade task: upgrade `next`/`sharp` deliberately, run
  the frontend tests and build, then re-run the audit.
- Backend dependency inventory completed successfully.
- No backend vulnerability scanner is configured in the Maven build. Add a
  dedicated scanner, such as OWASP Dependency-Check, Snyk, or the chosen company
  security tool, before a production release gate.

#### Indexes and query shape

Reviewed migrations:

- `V3__create_ticket.sql`
- `V5__create_comment_and_attachment.sql`
- `V7_1__add_admin_hardening.sql`
- `V16__create_subtype_forms.sql`
- `V17__create_routing_and_decisions.sql`
- `V18__add_ticket_workflow_context.sql`
- `V19__add_type_availability_and_capability.sql`

Findings:

- Ticket list filters have indexes for organization, ticket type, current
  state, ticket lead, business owner, subtype, parent ticket, routing rule,
  approver, client acceptance approver, and target user.
- Comment, attachment, audit, and status-history reads have ticket/date indexes.
- Dynamic field values have indexes for ticket, field definition, selected
  options, and user references.
- Configuration audit has an organization/date index for scoped review pages.

#### Pagination and bounds

Findings:

- Ticket list pagination clamps `pageSize` to a maximum of 100.
- User admin pagination clamps `pageSize` to a maximum of 100.
- Configuration audit pagination clamps `pageSize` to a maximum of 100.
- Active dynamic fields are limited to 50 per subtype.
- Active select options are limited to 100 per field.
- Text values, comments, names, labels, file names, and attachment sizes have
  explicit validation limits.

Follow-up:

- Ticket detail side panels currently load comments, attachments, audit entries,
  and status history as full arrays. This is fine for the current demo and
  mentor walkthrough, but long-running production tickets should use paged
  endpoints for those activity streams.
- Add a backend dependency vulnerability scanner to CI before using this as a
  production release gate.
- Resolve the current frontend dependency advisories through a deliberate
  Next/sharp compatibility update.

## T068 remaining release gate

### 2026-07-22 partial deploy verification

Status: blocked on Render redeploy/current-branch confirmation.

Local checks completed:

- Clean default-profile backend startup passed against
  `tf1_t068_clean_20260722`.
  - Flyway validated 22 production migrations.
  - Empty schema migrated to version `21`.
  - Hibernate validation succeeded.
- Clean demo-profile backend startup passed against
  `tf1_t068_demo_20260722`.
  - Flyway validated 24 migrations.
  - Empty schema migrated to demo version `22`.
  - Hibernate validation succeeded.
  - Demo tickets `TF-2100`, `TF-2101`, `TF-2102`, and `TF-2103` were present.
- Migrated backend startup passed against the restored rehearsal database
  `tf1_t064_restore_20260722`.
  - Flyway validated 22 migrations.
  - Schema was already at version `21`.
  - Hibernate validation succeeded.
- Frontend production build passed with Render-like variables:

  ```bash
  NEXT_PUBLIC_API_BASE_URL=/api \
    BACKEND_HOST=ticketflow1-api.onrender.com \
    npm run build
  ```

Live Render checks completed:

- `https://ticketflow1-web.onrender.com/login` returned HTTP `200`.
- `https://ticketflow1-api.onrender.com/swagger-ui.html` returned HTTP `200`
  after following the Swagger redirect.
- Demo login through the frontend proxy succeeded for
  `admin@ticketflow1.demo`.
- Fetching `TF-2100` through the frontend proxy returned HTTP `404`.
- The live ticket list still shows older `TF-100x` records, so the public
  Render environment is not yet on the new T066 demo seed.

Branch/deploy note:

- Local branch `dev2` is ahead of `origin/main`.
- Render cannot deploy local-only commits. Push/deploy the intended `dev2`
  commit, or repoint Render to the branch/commit that contains
  `0a07782 Seed service workflow demo data` and later release commits.

Before marking the feature complete:

1. Run a clean local deploy/build from a fresh database.
2. Run a migrated deploy/build from a copy of existing data.
3. Confirm the Render services are pointed at the intended branch/commit.
4. Execute the mentor walkthrough from `docs/demo-script.md`.
5. Confirm the frontend dependency advisory follow-up is either fixed or
   accepted by the project owner as a documented risk.
