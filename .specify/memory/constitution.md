<!--
Sync Impact Report
- Version change: 1.2.0 → 1.3.0
- Modified principles: VI (bounded subtype forms and deterministic routing)
- Added sections: none
- Removed sections: none
- Templates requiring updates:
  - .specify/templates/plan-template.md ✅ compatible (generic Constitution
    Check gate, no edits required)
  - .specify/templates/spec-template.md ✅ compatible
  - .specify/templates/tasks-template.md ✅ compatible
  - .specify/templates/checklist-template.md ✅ compatible
- Follow-up TODOs: none; feature 002 plan/tasks were synchronized in the same change
-->

# TicketFlow1 Ticketing Tool Constitution

## Core Principles

### I. Typed Lifecycles, Validated Transitions (NON-NEGOTIABLE)
Every ticket type has its own status lifecycle with an explicit, enforced set of
allowed transitions. A transition MUST be rejected by the backend if it is not in
the allowed set for that ticket's type and the actor's permissions. Status
transitions are never inferred implicitly from a generic `PATCH`; they go through
a dedicated transition operation that validates current-state → target-state
legality before applying it.

Transitions coupled to a business record or decision (for example creating,
approving, or rejecting a Change Proposal) MUST NOT be invokable through the
generic transition endpoint. Their owning domain service performs the state
change in the same transaction as the required business record and audit entry.

Lifecycles MAY be defined as configuration data (per organization and ticket
type) rather than hard-coded in Java — but the validation guarantee is absolute:
the transition engine loads the state machine from its definition and enforces
it, and MUST NEVER allow a transition that the definition does not contain.
Configurability changes *where the lifecycle is defined* (data, not code); it
does not weaken enforcement and does not erase type distinctions — a Change
Request's proposal-approval step remains a real, distinct part of its workflow,
not an option that quietly disappears.

### II. Audit Every Business Mutation (NON-NEGOTIABLE)
Every business-significant mutation—including status and tracked field changes,
comments, proposal decisions, and configuration changes—MUST write an audit
entry recording actor, action, target, relevant old/new value, and timestamp.
Ticket events and configuration events MAY use separate append-only stores when
their target shapes differ. Audit logging is not optional polish and is never
deferred or skipped to save time. If a feature mutates business state and has no
corresponding audit entry, the feature is incomplete.

Audit responses MUST respect the same organization and visibility boundaries as
their source data. In particular, a client-visible audit feed must never reveal
the body or existence of an INTERNAL comment.

### III. Permission-Based Access Is Core Business Value (NON-NEGOTIABLE)
Actions are gated by **permission**, enforced server-side, not just hidden in the
UI. Authorization checks MUST test permissions (e.g. `PROPOSAL_APPROVE`,
`TICKET_TRANSITION`, `COMMENT_INTERNAL_READ`, `COMMENT_INTERNAL_WRITE`,
`USER_MANAGE`) — they MUST NEVER branch on role *names*. Roles are configurable bundles of permissions (data, per
organization, seeded from defaults); a role that is renamed, added, or
re-scoped by an administrator must work without any code change, which is only
possible if code never references a role by name.

The **permission catalog is fixed in code** (developer-owned); only the
role→permission mapping is data. Two domain facts remain non-configurable:

- **Party axis** — every user is either CLIENT or TICKETFLOW1; an Organization
  represents a client tenant. This is structural: it drives org-scoping, INTERNAL-comment visibility, and
  `currentResponsibility`. A client can never become a provider by role
  reassignment.
- **Proposal approval gate** — approving or rejecting a change proposal requires
  the `PROPOSAL_APPROVE` permission AND CLIENT party.

The access model must never collapse to a single ADMIN/USER split — the
distinction is what makes this a process tool instead of generic CRUD.

### IV. Backend Workflow Before UI Polish
Within each build phase, workflow logic (entities, transitions, validation,
audit log, SLA calculation) is implemented and verified before the
corresponding UI is styled or polished. A working, testable backend slice
takes priority over a good-looking screen with no logic behind it.

### V. Small Verified Steps Over Big-Bang Generation
Prefer one working vertical slice over five scaffolds. Every claim that
something "works" MUST be backed by actually running it (a test, a curl
request, a manual check) — verify before reporting done. Do not generate large
swaths of unreviewed code in one pass when a smaller, checkable increment is
possible. As configuration-driven behavior is built up, the application MUST
remain runnable on its seeded defaults at every step — never a broken,
half-built state.

### VI. Bounded Configurability, No Overengineering
Configuration is data within a fixed schema — nothing more. Ticket **types**,
**workflows** (states + transitions), **subtypes**, **fixed-kind subtype form
definitions**, **deterministic assignment/approver routing references**, and
**roles** (as permission bundles) MAY be defined per organization at runtime.
This is deliberately bounded:

- NO per-tenant code, NO plugin system, NO scripting/rules engine.
- Form fields MUST use a developer-owned kind allowlist and bounded validation
  metadata; configuration cannot contain executable expressions, HTML, or SQL.
- Routing MAY only reference active, in-scope users and teams through explicit
  foreign keys; it cannot execute arbitrary conditions or invent permissions.
- Runtime configuration MUST NOT add physical columns or otherwise perform DDL.
- NO runtime-invented permission types — the permission catalog is fixed in code.
- The **party axis** (CLIENT/TICKETFLOW1) and the **defect severity** set stay
  fixed (severity drives the SLA formulas and is not configurable).

Outside those three configurable axes the simplicity rule holds absolutely: no
microservices, no premature abstractions, no speculative extensibility for
requirements that do not exist yet. Build the configurability the product
requires and stop there — do not rebuild Jira's full workflow engine. Three
similar lines of code beat a premature abstraction.

### VII. Teach, Don't Just Deliver
This project's purpose is dual: ship a working ticketing tool AND make Gregor
and Aleks stronger backend developers. For every non-trivial change, explain
what changed,
why, how to run and test it, and the underlying Spring/JPA/SQL concept involved
(layering, entity relationships, DTOs, validation, transactions, state
machines, migrations, security filters). Code should never just "appear" —
the reasoning behind it is part of the deliverable.

### VIII. Secure By Default, OWASP-Aware (NON-NEGOTIABLE)
Security is a first-class engineering requirement, not a late review pass. Any
new work that touches input handling, authentication, authorization, secrets,
storage, file upload, redirects, session handling, serialization, or external
requests MUST be checked against the OWASP Top 10 and the most common secure
coding failures for that area.

At minimum, code must avoid the usual classes of mistakes: injection
vulnerabilities, broken access control, sensitive data exposure, insecure
deserialization, security misconfiguration, use of known-vulnerable
dependencies, missing logging/monitoring for security-relevant events,
hardcoded secrets, and unsafe defaults. Prefer parameterized queries, explicit
allowlists, least privilege, server-side authorization, output encoding where
needed, and central security filters over ad-hoc checks.

If a change introduces a security-sensitive path, the change is incomplete
until the relevant threat is called out and the mitigation is visible in code,
tests, or review notes. "It should be fine" is not an acceptable security
argument.

Cookie-carried authentication MUST include CSRF protection for state-changing
browser requests. Production cookies MUST be `Secure`; demo credentials and
demo data MUST be isolated from production migrations/configuration. Tenant
scoping applies to administration and configuration endpoints as well as ticket
queries.

## Technology Stack

Fixed for this project; do not introduce alternative frameworks or swap
components mid-build:

- **Backend**: Java 21, Spring Boot, Spring Web, Spring Data JPA, Spring
  Security, PostgreSQL, Flyway, OpenAPI/Swagger.
- **Frontend**: Next.js, TypeScript, Tailwind CSS.
- **Dev/deployment**: Docker Compose (Postgres + backend), `.env.example`,
  seed/demo data, README with setup steps.

Fixed enumerations that are NOT configurable (party, severity,
comment visibility, audit action, proposal status, priority) are represented as
`TEXT` columns with `CHECK` constraints rather than native Postgres `ENUM`
types, so their value sets evolve symmetrically via a single migration.
Configurable sets (ticket type, ticket status, role) are lookup tables.
Deviating from the stack (e.g., swapping Flyway for Liquibase, or the frontend
framework) requires an explicit constitution amendment, not an ad-hoc choice.

## Development Workflow

Build order follows doc 02 §9 and MUST NOT be reordered to chase UI polish
early: backend foundation → ticket core → workflow/transitions → comments →
change proposals → defect SLA → frontend → polish/demo.

Every organization starts from **seeded default** types, workflows, and roles,
so the app is fully functional out of the box; configuration edits those
defaults rather than starting from an empty system. The application MUST remain
runnable on those seeded defaults at every step (Principle V).

If time runs short, the non-negotiables win over any additional feature:
typed-and-validated lifecycles, transition validation, permission-based access,
comments, audit log, defect SLA tracking, and the dashboard. Everything else
(attachments as real file storage, DISPUTE ticket type, business-calendar-
accurate SLA timers) is explicitly deferred past MVP.

## Governance

This constitution supersedes ad-hoc preferences expressed in any single
conversation. `CLAUDE.md` and `docs/02-product-requirements-and-build-brief.md`
are the source documents this constitution was derived from; where they
conflict with this document, this document — being the most recently
ratified and most specific to engineering practice — governs.

**Fixed guardrails** (may only change by further amendment, never by feature
work): the permission catalog is code-owned; the CLIENT/TICKETFLOW1 party axis
is structural and non-configurable; the defect severity set is fixed. Any task
that would make one of these runtime-configurable is out of scope by default.

**Amendment procedure**: propose the change, state the rationale, update this
file, bump the version per the policy below, and record the change in a Sync
Impact Report comment at the top of this file.

**Versioning policy** (semantic versioning applied to governance):
- MAJOR: backward-incompatible principle removal or redefinition.
- MINOR: a new principle or materially expanded section is added.
- PATCH: wording clarifications, typo fixes, non-semantic edits.

**Compliance review**: every plan produced by `/speckit-plan` must pass a
Constitution Check against these principles before task breakdown begins. Any
violation must be justified explicitly in that plan's Complexity Tracking
section or the plan must be revised.

**Version**: 1.3.0 | **Ratified**: 2026-07-02 | **Last Amended**: 2026-07-21
