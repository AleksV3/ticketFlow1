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
Existing ticket detail, lists, comments, attachments, history, and transitions
continue to use their original type/workflow until they naturally finish. No
bulk state compression or ticket-type FK rewrite is performed. Legacy creation
can be disabled later with the type activation controls after the rollout owner
confirms no users still need the old entry points.

The UI should label inactive legacy types as “Legacy” and not offer them in new
ticket forms once the rollout owner deactivates them. Reporting may group legacy
and successor keys using the fixed mapping above without changing stored ticket
identity.

## Backfill rules

- Existing tickets receive null `subtype_id`, parent, routing, approver, target
  user, and dynamic values. DTOs must support this legacy shape.
- Existing `DEFECT` types receive `DEFECT_SLA`; new `DFCT` types are seeded with
  the same capability. Severity remains fixed and existing SLA timestamps are
  untouched.
- Existing types start `active=true`; the workflow-seeding migration preserves
  that flag so deploys do not unexpectedly remove old creation paths. Operators
  can deactivate legacy types per organization after acceptance.
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

## T064 rehearsal record (2026-07-22)

- Created disposable local database `tf1_t064_base_20260722` and migrated it
  with an isolated copy of production migrations V1 through V15. Current-code
  Hibernate validation was disabled only for this baseline step because the
  current entity model expects the later additive tables.
- Inserted a sanitized legacy sample ticket `TF-9001` as `CHANGE_REQUEST` /
  `SUBMITTED` for `Sanitized Client A`, with one public comment, one attachment
  metadata row, one status-history row, and one audit row. No real customer data
  or Render data was used.
- Created a custom-format backup with `pg_dump -Fc` and restored it into
  `tf1_t064_restore_20260722`. The restored database was confirmed at Flyway
  version 15 with the sanitized sample ticket present.
- Ran the current backend against the restored database. Flyway validated 22
  migrations, applied V16 through V21, and ended at version 21
  (`backfill service request subtypes`). The Spring context then started
  successfully with Hibernate schema validation.
- Post-migration checks confirmed `TF-9001` still existed with original type,
  original state, one comment, one attachment, one status-history row, and one
  audit row. New nullable workflow-context columns (`subtype_id`,
  `parent_ticket_id`, `routing_rule_id`, `resolved_approver_id`,
  `target_user_id`) remained null for the legacy ticket, as designed.
- Successor type rows for `TASI`, `USR`, `DFCT`, and `REQ` were present for
  templates and cloned organizations. `DEFECT` and `DFCT` retained
  `DEFECT_SLA`; other types retained `STANDARD`.
- Rollback/recovery decision: do not run destructive Flyway undo. For an
  application-only rollback, redeploy the older artifact with
  `--spring.flyway.validate-on-migrate=false` as described above. For database
  recovery, restore the pre-migration backup and replay any accepted business
  events created after the backup point.
