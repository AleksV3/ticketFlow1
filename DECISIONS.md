# TicketFlow1 — Design Kernel (Decision Record)

The distilled set of human decisions that shaped TicketFlow1's specification,
data model, and task plan — the **why** behind the artifacts in
`specs/001-ticketing-mvp/`.

The spec-kit artifacts remain the authoritative **what**; this file is the
source of truth for **intent and provenance**. It is distilled from the team's
working prompts (`prompt-log.md`) down to the decisions that actually shaped the
design. Each entry: the decision, the artifact it shaped, and the rationale.

---

## Method

| Decision | Shaped | Rationale |
|---|---|---|
| Specification and planning **before** implementation | constitution, `specs/` | Agree the design first; no code ahead of an approved spec. |
| **Task-driven** build — follow the generated, dependency-ordered task list | `tasks.md` | A planned build order beats ad-hoc generation. |
| **Small, verified steps** — each phase ends runnable and checked | constitution (Principle V), `tasks.md` | Catch problems early; always demoable. |
| **Human-in-the-loop** visual task tracker | `docs/dashboard.html` | Keep the humans oriented across 70 tasks. |
| **Per-feature documentation** — one page per user story | `specs/001-ticketing-mvp/features/` | Each story stitches its own scenarios, API and tasks. |
| **Onboarding docs vault** for fast context | `ticketflow1-docs` | New sessions and teammates get architecture context quickly. |
| **Feature-first** presentation, deep tech/SQL second | doc ordering | Stakeholders review user value before implementation detail. |
| Spec-kit artifacts are the **single source of truth**, kept from the start | `specs/` | One canonical spec, not competing copies. |

## Product & domain

| Decision | Shaped | Rationale |
|---|---|---|
| Three ticket types (Change Request, Task, Defect), each with its **own validated lifecycle**; CR proposal-approval gate; Defect severity + SLA; `currentResponsibility`; comments; audit; dashboard | `spec.md` US1–US5 | The process fidelity is the product — not generic CRUD. |
| **Strict multi-tenant isolation** — a client user never sees another organization's tickets | FR-020 / FR-021 | Surfaced directly by observing that a client could otherwise see all tickets. |
| **Confirmation on sensitive actions** (approve / close / transition) | frontend UX | Prevent accidental irreversible state changes. |
| **Team project** — built by Gregor and Aleks; artifacts credit the team | constitution, docs | Shared ownership. |

## Architecture — the core of the kernel

| Decision | Shaped | Rationale |
|---|---|---|
| **Flexible value representation over static DB enums** — fixed sets are `TEXT` + `CHECK` | `data-model.md` | A `CHECK` evolves with one migration; static enums get hard to change as the codebase and tenant needs grow. |
| **Configurable ticket types & workflows** per client company | `spec.md` US8, config-driven transition engine (`plan.md`) | Different companies have different processes; a fixed three would not fit all. |
| **Configurable roles via permission-based access** — roles are permission bundles seeded with defaults; code checks permissions, never role names | `spec.md` US6/US7, FR-007/008/009 | An admin can add or rename a role without a code change. |
| **Integer (`bigint`) primary keys, not UUID** | `data-model.md` | For a single-database internal tool, sequential keys are simpler and sufficient; access is guarded by authorization, not id opacity. |
| **Versioned schema migrations (Flyway)** retained even with one database | `db/migration/` | Schema changes stay reviewable and reproducible. |
| **Product name only** — all artifacts use "TicketFlow1", no client/company name | all docs | Neutral, shippable branding. |
| **Audit log** as the canonical event record (over a plain "change log") | `audit/` | Records actor, action, old/new value, timestamp — a system of record. |

## Roadmap — future scope (from stakeholder input)

| Item | Shaped / target | Note |
|---|---|---|
| **Gmail-style master–detail UI** — list left, detail opens center | `specs/002-ticket-master-detail/` | Requested by the operations stakeholder. |
| **Workflow builder** — admins compose types/workflows in the UI | configurable workflows (US8) | The UI surface over the config model. |
| **Full-text ticket search** | future | |
| **LLM assistant** — "which tickets are open for me and how much time do I have?" | future / agent SDK | Natural-language ticket queries. |

## Provenance & reproducibility

- The team maintains two records of intent: `prompt-log.md` (the substantive
  prompts, chronological) and this **`DECISIONS.md`** (the distilled kernel).
- The spec-kit artifacts are regenerable from this kernel via
  `/speckit-specify` → `/speckit-plan` → `/speckit-tasks`. Fed back through that
  flow, the kernel should reproduce ~the current design; the artifacts hold the
  full detail. That reproducibility is what makes the kernel a genuine source of
  truth rather than just notes.
