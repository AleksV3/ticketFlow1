# Phase 2 verification

Verified on 2026-07-24 before starting Phase 3.

## Coverage

- `APPROVE_ALL_TICKETS` is a fixed-catalog permission granted only through
  TICKETFLOW1-party roles.
- An internal administrator can approve a pending protected workflow assigned
  to another developer; an unrelated developer remains forbidden.
- Approval decisions retain the actual actor in the decision and audit records.
- Role permission sets accept the full catalog, remove individual permissions,
  normalize duplicates, reject unknown or forbidden keys, and reject stale
  versions with `409`.
- Role creation and updates write before/after configuration audit snapshots.
- Fresh organization cloning preserves ticket-type capabilities, service
  subtypes, and protected transition operation metadata.
- Legacy client ticket keys and client acceptance/rejection flows remain
  compatible with the P0 approval changes.

## Commands and results

```text
backend: ./mvnw -q test
result:  72 tests passed

frontend: npm test
result:   4 files passed, 17 tests passed

frontend: npm run build
result:   Next.js production build and TypeScript checks passed
```

The backend suite includes PostgreSQL migrations through V29 using
Testcontainers, API integration tests, authorization boundaries, concurrency,
rollback, audit, and tenant-isolation checks.
