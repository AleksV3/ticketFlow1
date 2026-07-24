# Phase 3 verification

Verified on 2026-07-24 before starting Phase 4.

## Coverage

- Dashboard preferences are scoped by authenticated user and organization
  context, with separate uniqueness protection for client and internal scopes.
- Only developer-owned widget, ticket-filter, and theme keys can be written.
- Layout replacement is ordered, bounded, versioned, and rejects stale writes.
- Reset is idempotent and restores the current six-widget default catalog.
- Unknown writes are rejected; retired stored keys and inaccessible saved teams
  are ignored safely on read.
- All initial widgets render: my open tickets, my team's tickets, tickets by
  status, tickets by type, awaiting my approval, and recently updated tickets.
- Awaiting-approval results mirror decision authorization: designated
  approvers, assigned team leaders, and global approvers qualify; unrelated
  developers do not.
- Show/hide, move up/down, save, and reset controls are keyboard-operable.
- Persistence and isolation are covered across two users and two organizations.

## Commands and results

```text
backend: ./mvnw -q clean test
result:  73 tests passed

frontend: npm test
result:   5 files passed, 20 tests passed

frontend: npm run build
result:   Next.js production build and TypeScript checks passed

frontend: npm run test:e2e
result:   4 Playwright scenarios passed
```

The backend suite starts PostgreSQL through Testcontainers and applies Flyway
migrations through V30, matching the additive migration path used by the Neon
database when Cloud Run starts.
