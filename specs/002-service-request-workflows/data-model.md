# Data Model: Configurable Service Request Workflows

All configuration uses ordinary additive tables. No runtime DDL is permitted.
Tenant-owned records carry `organization_id`; template records may use a null
organization only where the existing workflow/type template model permits it.

## Configuration

### `ticket_subtype`

`id`, `ticket_type_id`, immutable `key`, editable `name`, `description`,
`active`, `sort_order`, `version`, timestamps. Unique `(ticket_type_id, key)`.
A referenced subtype is deactivated, never deleted.

### `subtype_field_definition`

`id`, `subtype_id`, immutable `key`, editable `label`, `help_text`, `field_kind`,
`required`, `visibility` (`PUBLIC` or `INTERNAL`), `active`, `sort_order`,
`min_length`, `max_length`, `min_number`, `max_number`, `version`, timestamps.
Constraints allow only the developer-owned field-kind enum and require bounds
to match the kind. Unique `(subtype_id, key)`.

### `subtype_field_option`

`id`, `field_definition_id`, immutable `key`, editable `label`, `active`,
`sort_order`, `version`, timestamps. Allowed only for select kinds. Unique
`(field_definition_id, key)`. Referenced options are deactivated, not deleted.

### `subtype_routing_rule`

`id`, `subtype_id`, optional `organization_id`, required `team_id`, optional
`primary_developer_id`, `fallback_developer_id`, `approver_id`, `active`,
`version`, timestamps. At most one active rule for the same subtype/scope.
Services verify all referenced users are active and belong to the team/scope.

### Type availability

Extend `ticket_type` with `active`, `sort_order`, and fixed `capability` metadata
(`STANDARD` or `DEFECT_SLA`). Existing organization ownership remains the type
availability boundary. Referenced types may be deactivated; only unused types
can be deleted. The capability is code-owned and cannot be invented by admins.

## Ticket Data

### `ticket` additions

- `subtype_id` nullable FK to `ticket_subtype` (`RESTRICT`). Null preserves old
  tickets during migration; required for newly created configured types.
- `parent_ticket_id` nullable self-FK (`RESTRICT`). Service validation enforces
  same tenant/scope, visibility, no self-parenting, and no ancestor cycle.
- `routing_rule_id` nullable FK records the rule snapshot source.
- `resolved_approver_id` nullable FK to `app_user`.
- `target_user_id` nullable FK to `app_user` plus `target_user_display_snapshot`.

Indexes: subtype, parent, target user, resolved approver, and
`(organization_id, parent_ticket_id)`. Parent closure queries non-terminal
children; deleting a parent with children is rejected.

### `ticket_field_value`

One row per `(ticket_id, field_definition_id)` with exactly one compatible
typed value: `text_value`, `number_value`, `date_value`, `boolean_value`,
`user_value_id`, or `team_value_id`. Single-select values reference an option.
Multi-select values use `ticket_field_value_option(value_id, option_id)`.
Database checks enforce one value representation; the service enforces kind,
required status, bounds, active definitions/options, visibility, and scope.

Used values remain linked to inactive definitions/options so history remains
readable. A display snapshot is stored for user/team references where required
for durable ticket history.

## Decisions and Audit

### `ticket_decision`

`id`, `ticket_id`, `kind` (`WORKFLOW_APPROVAL` or `CLIENT_ACCEPTANCE`),
`decision` (`APPROVED` or `REJECTED`), `actor_id`, `from_state_id`, `to_state_id`,
`reason`, `created_at`, and optimistic ticket version observed. Decisions are
append-only and written in the same transaction as state/history/audit changes.

Existing `configuration_audit` records every subtype, field, option, routing,
and availability mutation. Existing ticket audit records routing resolution,
dynamic-value changes without leaking internal values, parent links, target
user changes, decisions, and transitions.

## Deletion and Concurrency Rules

- Optimistic `version` fields prevent lost admin updates.
- Delete is allowed only when no dependent or historical reference exists.
- Deactivation blocks new use but never hides historical values.
- Configuration updates never rewrite existing ticket values or assignments.
- All repository reads used for mutation include tenant/type ownership checks;
  foreign IDs supplied by clients are never accepted solely because they exist.

