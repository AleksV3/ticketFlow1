# Requirements Traceability

This matrix is the implementation index for the 13 source todo items. Contract
names refer to files under `contracts/`; exact production paths remain subject
to the contract baseline and existing endpoint compatibility.

| Source change | Requirement / scenario | API or command | Persistence | Primary UI | Required tests |
|---|---|---|---|---|---|
| Global ticket approval | FR-002–FR-004; US2 | protected workflow approve/reject; permission and role references | fixed permission seed; existing decision/history/audit | role admin; ticket decision panel | assigned/global/unrelated/cross-tenant/stale matrix |
| Fix TASI approval | FR-001, FR-004; US1 | transition into approval, ticket detail commands, protected decisions | ticket approver/team, ticket decision, status history, audit | ticket decision panel | state entry, approver resolution, team lead, forbidden, rollback |
| Unlimited permissions per role | FR-005–FR-006; US3 | role create/update/get | role, role_permission, version if required | role admin | full catalog round-trip, remove one, duplicate, stale update |
| Editable dashboard | FR-007, FR-014; US4 | preference get/replace/reset; dashboard data | scoped user/org preference | dashboard editor and widgets | persistence, reset, unknown widget, isolation |
| Field-level visibility | FR-008–FR-009; US5 | field admin; creation form; ticket create/update/read | field-role-operation grants | field admin; ticket forms/detail | omission, direct mutation denial, legacy allow, role union |
| Ticket page layout | FR-016; US6 | none | none | ticket detail | mobile/desktop layout and keyboard regression |
| Ticket list filters | FR-010; US6 | existing list plus missing supported query filters | optional saved enabled-filter preference | ticket list | draft/apply/clear and backend filter contract |
| Teams page default | FR-011, FR-014; US7 | team preference get/update | scoped last-viewed team | teams page | restore, invalid membership, fallback, empty, isolation |
| Searchable selectors/cards | FR-012, FR-016; US7 | existing user/type references | none | user/role/team assignment screens | search, filters, keyboard, selection state |
| Workflow page improvements | FR-013, FR-015; US8 | workflow state rename/update | workflow_state; configuration audit | workflow/type administration | context, explicit open, rename validation, transition preservation |
| Full-width layout | FR-016; US9 | none | none | app shell and data-heavy pages | wide/mobile visual regression |
| Light mode | FR-014, FR-016; US9 | theme preference get/update | scoped theme preference | app shell/theme switcher | system default, persistence, hydration, WCAG contrast |
| Board description contrast | FR-016; US9 | none | none | team board ticket cards | light/dark contrast, long/truncated description |

## Verification ownership

- Backend integration tests own authorization, tenant scope, transactional
  persistence, response filtering, and conflict semantics.
- Backend unit tests own approval-policy branches and bounded preference/field
  policy rules.
- Frontend unit tests own command-driven rendering, draft/applied filter state,
  searchable controls, and theme initialization.
- End-to-end tests own the complete TASI path and persisted user experiences.
- Migration tests own additive upgrade safety and database uniqueness rules.
