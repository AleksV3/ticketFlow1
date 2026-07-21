# Administration API Contract

Base path: `/api/admin`. All endpoints require authentication, internal party,
the indicated fixed permission, tenant/type ownership checks, CSRF protection
for cookie requests, validation limits, and configuration audit.

## Subtypes (`TYPE_MANAGE`)

- `GET /ticket-types/{typeId}/subtypes?includeInactive=true`
- `POST /ticket-types/{typeId}/subtypes`
- `PUT /subtypes/{id}`
- `PUT /ticket-types/{typeId}/subtypes/order`
- `POST /subtypes/{id}/activate` and `/deactivate`
- `DELETE /subtypes/{id}` (unused only)

Create body: `{ key, name, description?, sortOrder? }`. Update body includes
`version`; `key` is rejected after first use. Ordering accepts all visible IDs
once, rejects duplicates/foreign IDs, and updates atomically.

## Fields and options (`TYPE_MANAGE`)

- `GET|POST /subtypes/{subtypeId}/fields`
- `PUT|DELETE /fields/{id}`; activation endpoints mirror subtype behavior
- `PUT /subtypes/{subtypeId}/fields/order`
- `GET|POST /fields/{fieldId}/options`
- `PUT|DELETE /field-options/{id}`; activation/order endpoints mirror fields

Field create body contains `key`, `label`, `helpText`, allowlisted `fieldKind`,
`required`, `visibility`, supported bounds, and order. Maximums: 50 active
fields/subtype, 100 options/select, keys 2–50, labels 1–120, help 1,000.
Unknown kind/bound combinations return field-level `400` errors.

## Routing (`TYPE_MANAGE`)

- `GET /subtypes/{subtypeId}/routing`
- `PUT /subtypes/{subtypeId}/routing`
- `DELETE /subtypes/{subtypeId}/routing` (deactivates when previously used)

Body: `{ organizationId?, teamId, primaryDeveloperId?, fallbackDeveloperId?,
approverId?, active, version? }`. The server requires an active, visible team;
developers must be active selectable team members; approver must be the team
leader or an explicitly selected active team member. Cross-scope IDs are
returned as `404`, not disclosed through validation text.

## Type availability (`WORKFLOW_MANAGE`)

Existing ticket-type administration gains `active`, `sortOrder`, and fixed
`capability`. Creating a client organization seeds active DFCT and REQ types.
TASI and USR are internal-only. Referenced types return `409` on hard delete
and may be deactivated. Admin-created capabilities are restricted to the
developer-owned allowlist.

## Reference form

`GET /api/reference/ticket-types/{typeId}/creation-form` requires
`TICKET_CREATE`. It returns the active authorized type, ordered active subtypes,
fields, and options only. Internal fields/configuration are never returned to a
client. Response includes configuration versions used for optimistic submit
validation.

## Errors

- `400` malformed input, invalid kind/bounds, incomplete reorder, inactive ref.
- `403` authenticated caller lacks permission/party.
- `404` absent or out-of-scope resource (same response).
- `409` version conflict or delete of referenced configuration.

Every mutation response returns the saved resource, its `version`, and active
state. Mutation audit contains actor, tenant, target kind/ID, action, and safe
old/new metadata; it never stores executable content or secrets.

