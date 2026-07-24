# Implementation Plan: Priority Fixes and UX Improvements

**Branch**: `003-priority-fixes-and-ux` | **Spec**: `spec.md`

## Summary

Restore the broken TASI approval vertical slice first, then add an explicitly
tenant-scoped global approval permission and harden role permission set updates.
After those security-sensitive paths pass backend and integration gates, add
bounded user preferences, field-level authorization, and the grouped UI/UX
improvements.

## Existing-System Findings

- Approval records and protected workflow decisions already exist from feature
  `002`; the repair must trace and correct that path instead of creating a
  parallel approval mechanism.
- Roles already use a many-to-many permission model and migration V9 permits
  multiple roles per user. The role limit work should locate request/UI/service
  assumptions rather than redesign RBAC.
- The current stack remains Spring Boot/JPA/Flyway/PostgreSQL and
  Next.js/TypeScript/Tailwind.
- Existing components in `TicketUi`, `TicketExtras`,
  `WorkflowConfigurationPanels`, and the ticket-type selector should be evolved
  or reused rather than duplicated.

## Technical Approach

### 1. Approval diagnosis and repair

Build a failing integration-test matrix before changing behavior. Trace one TASI
ticket from `ANALYSIS` into `PENDING_APPROVAL`, verifying:

1. protected-operation metadata on the configured transition;
2. approval/decision record creation and active uniqueness;
3. designated approver and team-lead resolution;
4. actor tenant, permission, party, relationship, and current-state checks;
5. command availability returned in ticket detail;
6. frontend button rendering and CSRF-authenticated request;
7. atomic decision plus state/history/audit persistence.

Correct the earliest broken invariant and keep protected decisions unavailable
through the generic transition endpoint.

### 2. Global approval permission

Add `APPROVE_ALL_TICKETS` to the fixed permission catalog and seeded
organization role defaults only where explicitly desired. Centralize the
authorization predicate:

```text
same tenant
AND active pending approval
AND valid protected transition
AND (
  actor matches normal resolved approval relationship
  OR actor has APPROVE_ALL_TICKETS
)
```

Do not encode role names. Return `403` for an authenticated in-scope actor who
lacks authority, `404` where tenant non-disclosure requires it, and `409` for a
stale/already-decided approval.

### 3. Unlimited role permission sets

Remove UI/request/service count caps, accept a set of catalog permission IDs,
validate every ID, reject cross-scope/unknown IDs, de-duplicate deterministically,
and replace the role mapping transactionally. Add optimistic concurrency using
the existing version/ETag convention, or introduce a role version if none
exists. Database uniqueness remains the final duplicate guard.

### 4. Preference model

Use bounded JSON only for ordered dashboard widget keys and enabled filter keys;
use explicit scalar preferences for theme and last-viewed team when practical.
Scope preference rows by `(user_id, organization_id)` and validate all values
against developer-owned catalogs or current team membership. Provide get,
replace, and reset operations. Never store executable layout definitions.

### 5. Field-level authorization

Add field-to-role grants with an operation enum (`VIEW`, `EDIT`, `CREATE`) and a
unique constraint per field/role/operation. Treat zero configured grants as the
backward-compatible legacy allow state. Apply one shared policy in:

- creation-form definition responses;
- ticket detail/list serialization;
- create and edit command validation;
- administration previews.

Fetch authorized field IDs in batches to avoid per-field/per-ticket queries.
Never rely on frontend hiding for protection.

### 6. UI/UX slices

- Separate comments and attachments into full-width responsive sections.
- Keep search visible; introduce collapsed draft filters with Apply/Clear.
- Validate and restore last-viewed assigned team.
- Extract/reuse an accessible searchable combobox and selectable user cards.
- Make workflow-only actions contextual; rename states in place.
- Replace the global narrow container with page-aware width variants.
- Implement theme tokens with system-first initialization and persisted
  preference, then correct team-card description contrast in both themes.

## Data Model Changes

Additive Flyway migrations are expected for:

- fixed permission catalog seed for `APPROVE_ALL_TICKETS`;
- role versioning only if current concurrency protection is absent;
- `user_organization_preference` with unique user/organization scope,
  dashboard layout, theme, enabled filters, and last-viewed team reference;
- `subtype_field_role_grant` with field, role, operation, and unique constraint.

Approval repair should not add a second approval table. Schema changes are
allowed only if tests prove an invariant (such as one active decision per
ticket/operation) is currently unenforced.

## API and Contract Changes

- Extend permission reference and role create/update responses/requests.
- Preserve the existing protected approve/reject endpoints; document global
  override authorization and `403`/`404`/`409` semantics.
- Add scoped preference get/replace/reset endpoints.
- Extend field administration contracts with view/edit/create role grants.
- Add filter parameters only for filters the backend does not already support.
- Extend workflow state update contract for in-place rename and version checks.
- Ticket detail remains the source of truth for available approval commands.

Exact DTOs and response examples belong in `contracts/` before implementation.

## Constitution Check

- **I Typed lifecycles**: Pass. Approval remains a protected domain command and
  can transition only across the configured approval edge.
- **II Audit**: Pass if approval, role, field-rule, preference where material,
  and workflow rename mutations create the appropriate audit/history entries.
- **III Permission access**: Pass. A new fixed catalog permission is explicitly
  allowed; authorization checks permission plus tenant and approval state, never
  a role name.
- **IV Backend before UI**: Pass. P0/P1 backend tests and contracts gate UI.
- **V Small verified steps**: Pass. Each phase ends with a vertical verification
  gate; the bug begins with a reproducible failing test.
- **VI Bounded configurability**: Pass. Widget/filter catalogs and permission
  keys remain developer-owned; no scripts, queries, runtime DDL, or invented
  permissions.
- **VII Teach**: Pass if the approval root cause, transactional boundary, RBAC
  set semantics, and field-response filtering are documented.
- **VIII Secure by default**: Pass if tenant tests, direct-API field attacks,
  stale decisions, mass assignment, CSRF, and response leakage are covered.

No constitution exception or complexity waiver is required.

## Delivery Gates

1. Reproduce the TASI failure with a backend integration test and identify the
   broken invariant before implementing a fix.
2. Complete the assigned/team-lead/global/unrelated/cross-tenant/stale approval
   matrix before changing approval UI.
3. Round-trip the full permission catalog and concurrent role edits before P2.
4. Review field-grant response shaping and direct mutation denial before its UI.
5. Verify preference isolation with two users and two organizations.
6. Run responsive, keyboard, contrast, backend, frontend, and end-to-end suites
   before release.

## Risks and Mitigations

- **Authorization regression**: central predicate plus matrix tests and no
  role-name branching.
- **Cross-tenant override**: tenant lookup precedes global permission evaluation.
- **Duplicate/stale decisions**: transactional locking or optimistic versioning,
  database invariant, and `409`.
- **Sensitive field leakage**: shared server-side projection policy tested at
  every response surface.
- **Preference overengineering**: fixed catalogs and small scoped record; no
  general page builder.
- **Theme flash/contrast regression**: early theme bootstrap plus automated and
  manual checks in both modes.

## Explicit Non-goals

- No new workflow engine or replacement approval subsystem.
- No runtime-created permission keys.
- No arbitrary dashboard widgets, CSS, scripts, formulas, or filter queries.
- No cross-organization global administrator bypass.
- No unrelated redesign of feature `002` ticket types and workflows.
