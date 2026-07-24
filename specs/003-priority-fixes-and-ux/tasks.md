# Tasks: Priority Fixes and UX Improvements

**Status**: Phase 1 implemented — T013 integration execution blocked by unavailable Docker

**Input**: `spec.md`, `plan.md`, feature `002` approval contracts, current
constitution, and the source todo list.

## Phase 0: Baseline and contracts

- [x] T001 Map every todo item to a requirement, acceptance scenario, API,
  persistence change, UI surface, and test.
- [x] T002 Capture the current TASI approval state/record/authorization/command
  flow and add a failing regression test reproducing “nobody can approve”.
- [x] T003 Write approval, role, preferences, field grants, filters, and workflow
  rename contracts with `403`/`404`/`409` behavior.
- [x] T004 Threat-model global approval, field-value disclosure, direct field
  mutation, role mass assignment, stale decisions, and preference tenant scope.
- [x] T005 **Verify**: root cause is documented and no approval implementation
  begins without a reproducible failing test.

## Phase 1: TASI approval P0

- [x] T006 Trace and fix protected transition metadata and approval creation.
- [x] T007 Fix designated approver and required team-lead resolution.
- [x] T008 Fix backend decision authorization and clear forbidden/conflict
  responses.
- [x] T009 Ensure approve/reject atomically persists decision, correct next state,
  status history, and audit.
- [x] T010 Ensure ticket detail advertises available commands for the actor.
- [x] T011 Fix approval control visibility/enabled state and request handling.
- [x] T012 Test assigned approver, team lead, unrelated user, inactive approver,
  stale decision, rollback, and cross-organization isolation.
- [ ] T013 **Verify**: execute complete TASI approve and reject paths through API
  and UI with attributable audit/history.

## Phase 2: Global approval and role sets P1

- [ ] T014 Add and migrate fixed catalog permission `APPROVE_ALL_TICKETS`.
- [ ] T015 Extend the central approval predicate with the tenant-scoped override.
- [ ] T016 Expose the new permission through role administration.
- [ ] T017 Remove frontend, DTO, validation, and service permission-count caps.
- [ ] T018 Implement validated transactional replace-set role updates with
  de-duplication and concurrency protection.
- [ ] T019 Add database uniqueness/invariant migration only where missing.
- [ ] T020 Audit global decisions and role permission changes.
- [ ] T021 Test Developer A assignment, global administrator decision, Developer
  B denial, cross-tenant denial, full-catalog role round-trip, removal of one
  permission, duplicates, and stale edits.
- [ ] T022 **Verify**: P0/P1 backend, frontend, and authorization suites pass
  before starting P2.

## Phase 3: Dashboard preferences P2

- [ ] T023 Add scoped user/organization preference schema and domain model.
- [ ] T024 Add bounded dashboard layout get/replace/reset endpoints.
- [ ] T025 Add “awaiting my approval” and other missing widget queries.
- [ ] T026 Build widget visibility, reorder, save, and reset controls.
- [ ] T027 Test invalid widget keys, retired widgets, reload persistence, reset,
  and two-user/two-organization isolation.
- [ ] T028 **Verify**: all initial widgets render and preferences remain isolated.

## Phase 4: Field-level visibility P2

- [ ] T029 Add field-role-operation grant schema, entities, and repositories.
- [ ] T030 Implement backward-compatible shared VIEW/EDIT/CREATE authorization.
- [ ] T031 Filter creation references and ticket response values server-side.
- [ ] T032 Enforce create/edit rules against direct and bulk API mutations.
- [ ] T033 Extend field administration UI for role grants.
- [ ] T034 Audit field-grant configuration changes.
- [ ] T035 Test no-rule compatibility, multi-role union, view-only, create-only,
  role removal, direct mutation denial, response omission, and tenant isolation.
- [ ] T036 **Verify**: restricted values are absent from every unauthorized API
  and UI surface.

## Phase 5: Ticket, team, and user UX P3

- [ ] T037 Make comments and attachments separate full-width responsive rows.
- [ ] T038 Separate ticket filter draft/applied state; add collapsed Show,
  Apply, Clear, and supported filter controls.
- [ ] T039 Add backend filter support and contract tests for any missing filter.
- [ ] T040 Persist and validate last-viewed assigned team with correct fallbacks
  and empty state.
- [ ] T041 Extract/reuse the searchable combobox selection pattern.
- [ ] T042 Build accessible searchable/filterable user selection cards.
- [ ] T043 Test no implicit filter requests, clear/apply semantics, responsive
  ticket layout, invalid saved team, keyboard combobox, and card selection.
- [ ] T044 **Verify**: ticket/team/user flows pass unit and end-to-end tests.

## Phase 6: Workflow administration P3

- [ ] T045 Contextualize “Create ticket type” below persisted workflow edit
  canvas only.
- [ ] T046 Stop automatic subtype-form opening; require explicit type selection.
- [ ] T047 Add versioned in-place workflow state rename with blank/duplicate
  validation and transition preservation.
- [ ] T048 Record workflow state rename in configuration audit.
- [ ] T049 Test create/edit context, explicit subtype opening, duplicate/blank
  rename, connected transitions, stale edit, and audit.
- [ ] T050 **Verify**: rename a connected state and execute its preserved path.

## Phase 7: Layout, theme, and contrast P3

- [ ] T051 Add page-aware full-width shell variants for data-heavy screens.
- [ ] T052 Define semantic light/dark design tokens and theme initialization.
- [ ] T053 Persist theme per user and use system theme when no preference exists.
- [ ] T054 Correct team-board description contrast and truncation in both themes.
- [ ] T055 Test hydration/theme initialization, preference reload/isolation,
  desktop/mobile layout, long descriptions, focus, and WCAG AA contrast.
- [ ] T056 **Verify**: keyboard and visual review passes in light/dark modes at
  mobile and wide desktop widths.

## Phase 8: Release

- [ ] T057 Run full backend, frontend, end-to-end, migration, and two-tenant
  isolation suites.
- [ ] T058 Rehearse additive migrations against fresh and current database
  snapshots and document rollback/recovery.
- [ ] T059 Update README, contracts, demo data, technical deep dive, and approval
  root-cause notes.
- [ ] T060 Review query counts/indexes for field projection, dashboard widgets,
  filters, and preference loads.
- [ ] T061 **Verify**: clean deploy, migrated deploy, security review, and
  acceptance walkthrough pass.

## Dependency Order

Phase 0 blocks all implementation. Phase 1 blocks approval UI changes. Phase 2
blocks P2/P3 work. Phases 3 and 4 may proceed independently after Phase 2.
Phase 5 depends on preference primitives only for saved filters/teams. Phase 7
depends on the preference API for theme persistence. Release requires every
selected priority phase and its verification gate.
