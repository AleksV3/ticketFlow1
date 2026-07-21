# Implementation Plan: Configurable Service Request Workflows

**Branch**: `002-service-request-workflows` | **Spec**: `spec.md`

## Summary

Extend the existing monolith with bounded, data-driven subtype forms and
routing; relationship-aware approval commands; four revised workflows;
organization type availability; and parent/subtickets. Continue using Spring
Boot/JPA/Flyway/PostgreSQL and Next.js/TypeScript/Tailwind.

## Technical Approach

1. Add subtype, dynamic-field definition/option/value, routing-rule, approval,
   type availability/active state, and parent-ticket schema through additive
   Flyway migrations.
2. Add backend configuration services with tenant scope, fixed field-kind
   allowlist, safe deletion/deactivation, configuration audit, and reference
   endpoints for dynamic creation forms.
3. Extend ticket creation to validate subtype values, directory references,
   routing, parent scope, and assignments in one transaction.
4. Implement TASI/USR approval and REQ client-acceptance as protected domain
   commands, not generic transitions.
5. Seed/migrate the four workflows and replace hard-coded `DEFECT` key checks
   with an explicit capability while keeping severity fixed.
6. Add admin subtype/form/routing UI, dynamic ticket creation UI, parent/child
   detail UI, and filters/board metadata only after backend verification.

## Constitution Check

- **I Typed lifecycles**: Pass if all state moves use configured edges and
  approval decisions own their protected transitions.
- **II Audit**: Pass if all new ticket/configuration mutations are audited.
- **III Permission access**: Pass if approvals combine fixed permissions with
  party, tenant, and assignment relationship checks; no role-name checks.
- **IV Backend before UI**: Plan order complies.
- **V Small verified steps**: Each phase ends with a runnable seeded vertical
  slice and verification task.
- **VI Bounded configurability**: Pass under constitution 1.3.0. Fixed-schema,
  fixed-kind form metadata and deterministic routing references are permitted;
  scripts, expressions, SQL, runtime permissions, arbitrary code, and runtime
  DDL remain prohibited.
- **VII Teach**: Add migration/domain explanations and an updated deep-dive.
- **VIII Secure by default**: Threat-model directory search, dynamic inputs,
  reference IDs, tenant scope, mass assignment, and approval bypass.

## Delivery Gates

1. Requirements/open decisions resolved and constitution amended.
2. Data model + contracts reviewed before migration implementation.
3. Dynamic configuration backend verified before ticket-create integration.
4. Workflow/approval matrices verified before UI work.
5. Cross-tenant integration suite and migration rehearsal pass before deploy.

## Explicit Non-goals

- No scripting/rules engine or arbitrary formulas.
- No runtime-created permission keys.
- No physical ticket columns created at runtime.
- No external identity-provider integration in this feature; USR search uses
  tenant-scoped TicketFlow1 `app_user` records behind an adapter boundary.
- No automatic parent closure or cross-ticket workflow orchestration in the
  first slice.
