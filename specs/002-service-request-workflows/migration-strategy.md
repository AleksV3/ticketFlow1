# Migration and Backfill Strategy

The migration is additive and preserves every existing ticket, workflow state,
status-history row, proposal, comment, attachment, assignment, and audit row.
It does not reinterpret old decisions as new workflow decisions.

## Legacy mapping

| Existing type | Successor for new work | Existing ticket treatment |
|---|---|---|
| `CHANGE_REQUEST` | `REQ` | Remains on its original workflow with proposal history intact; legacy type is later deactivated for creation |
| `TASK` | `TASI` | Remains on its original task workflow; no subtype is invented from free text |
| `DEFECT` | `DFCT` | Remains readable with its original state/history and keeps defect SLA capability |

New TASI, USR, DFCT, and REQ types/workflows are seeded alongside legacy rows.
After verification, legacy types are marked inactive, which blocks only new
creation. Existing ticket detail, lists, comments, attachments, history, and
transitions continue to use their original type/workflow until they naturally
finish. No bulk state compression or ticket-type FK rewrite is performed.

The UI labels inactive legacy types as “Legacy” and does not offer them in new
ticket forms. Reporting may group legacy and successor keys using the fixed
mapping above without changing stored ticket identity.

## Backfill rules

- Existing tickets receive null `subtype_id`, parent, routing, approver, target
  user, and dynamic values. DTOs must support this legacy shape.
- Existing `DEFECT` types receive `DEFECT_SLA`; new `DFCT` types are seeded with
  the same capability. Severity remains fixed and existing SLA timestamps are
  untouched.
- Existing types start `active=true`; the workflow-seeding migration explicitly
  deactivates legacy types only after successor types exist for the same scope.
- Organization-owned successor configuration is cloned from templates with the
  existing `clone_org_templates` mechanism extended additively.
- Existing assignments are not re-routed. Routing applies only on creation or
  an explicit, audited authorized reassignment.

## Deployment and rollback

1. Deploy additive schema while old code continues to ignore it.
2. Deploy entities/read paths that tolerate null legacy subtype data.
3. Seed successor workflows/types/configuration and validate per organization.
4. Enable new create paths, then deactivate legacy creation.

Application rollback is safe while only additive schema is present. Once new
tickets use successor types, rollback means redeploying the previous application
with new creation disabled; do not drop new tables. Database recovery uses the
pre-migration backup plus replayed business events, not a destructive Flyway
undo. A sanitized current-schema rehearsal is required by T064.

An older application artifact does not contain the newer migration files, so
its normal Flyway validation intentionally rejects the newer history rows. For
an emergency application-only rollback, start that older artifact with
`--spring.flyway.validate-on-migrate=false`. Flyway then leaves the newer schema
untouched and Hibernate still validates the older entity model. Restore normal
Flyway validation when rolling forward again; never run `repair`, delete schema
history, or drop the additive tables as part of an application rollback.

## T025 verification record (2026-07-21)

- Existing local schema migrated from v13 through v19; all 20 migrations and
  Hibernate validation passed.
- Disposable empty database `ticketflow1_feature_verify` migrated from empty to
  v19; all migrations and Hibernate validation passed.
- Detached pre-feature commit `4a45bf5` started successfully against the v19
  database with the explicit rollback setting above; no schema change ran.
