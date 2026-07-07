# Feature Docs — Index

One page per user story from [spec.md](../spec.md), each stitching together
its acceptance scenarios, workflow diagram, API endpoints, and build tasks
into a single read. These are a **thin overlay, not a copy** — the content
below still lives in `spec.md`/`plan.md`/`contracts/`/`tasks.md`; these pages
just point at the right slice of each so you don't have to jump between five
files to understand one feature. If a page and its source ever disagree, the
source file wins — treat that as a bug in this index, not in the source.

| # | Feature | Priority | Phase(s) | Doc |
|---|---|---|---|---|
| US1 | Change Request lifecycle with proposal approval | P1 | 2, 3, 5, 7 | [US1-change-request.md](US1-change-request.md) |
| US2 | Task lifecycle without proposal approval | P2 | 2, 3, 7 | [US2-task-lifecycle.md](US2-task-lifecycle.md) |
| US3 | Defect severity & SLA tracking | P1 | 2, 3, 6, 7 | [US3-defect-sla.md](US3-defect-sla.md) |
| US4 | Comments & audit trail | P2 | 3, 4, 7 | [US4-comments-audit.md](US4-comments-audit.md) |
| US5 | Dashboard overview | P2 | 2, 7 | [US5-dashboard.md](US5-dashboard.md) |
| US6 | Permission-based access & admin | P1 | 1, 7 | [US6-roles-admin.md](US6-roles-admin.md) |

Cross-cutting docs these pages don't replace:

- [spec.md](../spec.md) — full requirements, all 22 FRs, edge cases, assumptions
- [plan.md](../plan.md) — architecture, Constitution Check, project structure
- [data-model.md](../data-model.md) — ERD and every entity's fields
- [contracts/](../contracts/) — full request/response shapes for every endpoint
- [tasks.md](../tasks.md) — all 69 tasks with dependencies
- [../../../docs/dashboard.html](../../../docs/dashboard.html) — visual, checkbox-tracked progress across all phases
- [../../../docs/mockups/](../../../docs/mockups/) — AI-generated frontend visual references for Phase 7 (login, dashboard, ticket list/detail, admin). Reference only — the README there lists 4 known bugs versus this spec, don't build components by eyeballing the images directly.
