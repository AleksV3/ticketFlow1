# Tasks: Configurable Service Request Workflows

**Status**: In progress — requirements and governance decisions are resolved.

**Input**: `spec.md`, `plan.md`, the existing `001-ticketing-mvp` contracts and
data model, and the TicketFlow1 constitution.

## Format

`[ID] [P?] [Story?] Description` — `[P]` means independent files/no unfinished
dependency; all tasks remain unchecked until implemented and verified.

## Phase 0: Requirements and governance gate

- [x] T001 Confirm USR directory source (`app_user`, separate managed directory, or external identity provider).
- [x] T002 Confirm TASI/USR terminal rejection behavior and required rejection/correction reason visibility.
- [x] T003 Confirm REQ client-approver relationship rule and parent-close behavior.
- [x] T004 Define the first field templates for every seeded TASI and USR subtype, including required flags and public/internal visibility.
- [x] T005 Amend constitution Principle VI to permit bounded fixed-schema subtype forms and deterministic routing metadata; bump the constitution minor version and record template/spec impacts.
- [x] T006 Review and approve the four workflow diagrams and transition actor matrix as the implementation baseline; final mentor acceptance remains T068.
- [x] T007 **Verify**: no implementation begins while T001–T006 contain unresolved decisions.

## Phase 1: Detailed design and contracts

- [x] T008 Write `data-model.md` for subtype, field definition/option/value, routing, approval, type availability, and parent-ticket relationships, indexes, deletion rules, and audit targets.
- [x] T009 [P] Write admin contracts for subtype, field, option, routing, activation, safe deletion, and organization type availability.
- [x] T010 [P] Write ticket contracts for dynamic creation form retrieval/submission, target-user search, parent/subticket creation, approval commands, and enriched detail/list responses.
- [x] T011 [P] Write the exact transition matrix with required permission, party, responsibility-after, reason rule, and protected operation kind for every edge.
- [x] T012 Threat-model tenant data leaks, target-user enumeration, dynamic-value injection, mass assignment, approval bypass, cyclic parents, and unsafe configuration deletion.
- [x] T013 Define migration/backfill mapping from `CHANGE_REQUEST`/`TASK`/`DEFECT` to the approved new configuration without losing existing history.
- [x] T014 **Verify**: contracts and data model satisfy every FR and constitution check.

## Phase 2: Configuration schema and domain

- [x] T015 Add an additive Flyway migration for ticket subtype definitions, active/order metadata, field definitions, options, typed values, and required constraints/indexes.
- [x] T016 Add routing-rule and explicit approval/decision tables with tenant-safe foreign keys and audit metadata.
- [x] T017 Add nullable `parent_ticket_id` and subtype reference to ticket with safe indexes; leave existing tickets valid.
- [x] T018 Add active/capability metadata needed for organization type availability and DFCT SLA behavior without runtime DDL.
- [x] T019 [P] Implement subtype/field/option entities and repositories.
- [x] T020 [P] Implement typed field-value entities/repositories and fixed field-kind enum.
- [x] T021 [P] Implement routing and approval entities/repositories.
- [x] T022 Implement safe delete/deactivate reference checks and immutable-used-key rules.
- [x] T023 Record every new configuration mutation in configuration audit.
- [x] T024 [P] Unit-test typed validation, option membership, safe deletion, active state, and ordering.
- [x] T025 **Verify**: migrate a fresh DB and a current DB snapshot; Hibernate validation and rollback rehearsal pass.

## Phase 3: Configuration APIs

- [x] T026 Implement tenant-scoped subtype CRUD/reorder/activation endpoints gated by `TYPE_MANAGE`.
- [x] T027 Implement tenant-scoped field/option CRUD/reorder/activation endpoints with length/count bounds and allowlisted kinds.
- [x] T028 Implement routing-rule administration with active/same-scope team, developer, fallback, and approver validation.
- [x] T029 Implement organization type add/rename/workflow/activation and unused-only hard deletion.
- [x] T030 Implement creation-form reference endpoint returning only active authorized definitions/options.
- [x] T031 Implement tenant-safe target-user autocomplete with minimum query length, bounded results, and no cross-org leakage.
- [x] T032 [P] Integration-test every admin mutation, audit entry, cross-scope denial, manipulated ID, and referenced-delete conflict.
- [x] T033 **Verify**: configure a new subtype, select field, option, and routing rule entirely through APIs without schema/code changes.

## Phase 4: Dynamic ticket creation, routing, and subtickets

- [x] T034 Extend create-ticket DTO/domain service with subtype ID, dynamic values, and optional parent ticket key.
- [x] T035 Validate required/unknown/type/length/option/reference values against one server-loaded definition snapshot.
- [x] T036 Resolve routing deterministically and atomically assign team/developer/approver; allow audited authorized overrides only.
- [x] T037 Enforce USR NEW versus MODIFY/DELETE target-user rules and store immutable ID plus display snapshot.
- [x] T038 Enforce parent tenant/type visibility, prevent self-parenting and ancestor cycles, and define inheritance allowlist.
- [x] T039 Extend detail/list/board responses and filters with subtype, dynamic values as authorized, routing, parent, and child progress.
- [x] T040 Audit creation values without leaking internal/sensitive values to client-visible feeds.
- [x] T041 [P] Test routing, dynamic validation, USR search/reference rules, inheritance, cycles, tenant isolation, and transactional rollback.
- [x] T042 **Verify**: create TASI/FIREWALL, USR/NEW, USR/MODIFY, and a child ticket through API and inspect assignments/audit.

## Phase 5: Workflow migration and relationship-aware approvals

- [x] T043 Seed/migrate approved TASI and USR workflows with correction loops and protected approval operations.
- [x] T044 Seed/migrate approved DFCT workflow while preserving fixed severity/SLA behavior via capability rather than literal `DEFECT` checks.
- [x] T045 Seed/migrate approved REQ workflow with client acceptance and implementation/deployment loop.
- [x] T046 Implement correction-return command/reason persistence atomically with transition/history/audit/comment visibility.
- [x] T047 Implement TASI/USR submit/approve/reject domain commands; resolve assigned team leader/designated approver and prevent unauthorized self-approval.
- [x] T048 Implement REQ client acceptance/rejection command; require same-org business owner or approved permission rule.
- [x] T049 Ensure protected transitions never appear in or execute through the generic transition endpoint.
- [x] T050 [P] Matrix-test every legal/illegal transition, repeated loop, permission, party, relationship, stale decision, and rollback case.
- [x] T051 **Verify**: execute complete TASI, USR, DFCT, and REQ happy paths plus correction/rejection loops using multiple actors.

## Phase 6: Administration and ticket UI

- [x] T052 Build subtype/field/option editor with ordering, activation, safe-delete feedback, and accessible controls.
- [x] T053 Build routing editor for team/developer/fallback/approver configuration and validation feedback.
- [x] T054 Extend organization workflow/type administration with internal types, active state, defaults, and unused-only deletion.
- [x] T055 Build dynamic ticket creation form that reloads subtype definitions and clears stale hidden values safely.
- [x] T056 Build USR target-user autocomplete with loading, empty, selection, and authorization-safe error states.
- [x] T057 Add parent/subticket creation, hierarchy, progress, and navigation to ticket detail.
- [x] T058 Add approval panels driven by server-provided commands and resolved approver information.
- [x] T059 Add subtype and parent/child filters/metadata to ticket list and team boards.
- [x] T060 [P] Add frontend unit tests for dynamic rendering, subtype changes, stale-value clearing, user search, approval visibility, and hierarchy.
- [x] T061 Add end-to-end flows for TASI routing/approval, USR MODIFY search, DFCT loop, REQ client acceptance, and subticket creation.
- [x] T062 **Verify**: keyboard, responsive, permission-role, and no-console-error review passes.

## Phase 7: Release and documentation

- [x] T063 Run full backend/frontend suites and two-organization isolation suite.
- [x] T064 Rehearse migration against a sanitized current-schema backup and document rollback/recovery.
- [x] T065 Update README, presentation guide, technical deep dive, OpenAPI contracts, and demo script with the new model and workflows.
- [ ] T066 Seed a concise demo showing runtime field creation, automatic routing, relationship approval, client acceptance, and a subticket.
- [ ] T067 Review logs/audit privacy, dependency/security checks, query indexes, pagination, and dynamic-field limits.
- [ ] T068 **Verify**: clean deploy, migrated deploy, and mentor acceptance walkthrough pass; mark the feature complete only after all required tasks are checked.

## Dependency order

Phase 0 blocks all implementation. Phase 2 requires approved Phase 1 design.
Phase 3 blocks dynamic ticket creation. Phase 4 blocks workflow approvals where
routing relationships determine the approver. Backend Phase 5 blocks Phase 6
approval UI. Release verification requires all selected P1 stories; subtickets
may be delivered as the P2 slice after the four workflows if schedule requires.
