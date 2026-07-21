# Specification Quality Checklist: Configurable Service Request Workflows

- [x] User scenarios cover TASI, USR, DFCT, REQ, type availability, subtickets, and administration.
- [x] Workflow states, loops, actors, and protected decisions are explicit.
- [x] Dynamic fields use fixed-schema configuration rather than runtime DDL.
- [x] Safe deletion/deactivation and historical readability are specified.
- [x] Tenant isolation, approval bypass, user enumeration, and audit privacy are addressed.
- [x] Tasks separate backend rules from UI and include verification gates.
- [x] USR directory source is confirmed: tenant-scoped TicketFlow1 `app_user`.
- [x] TASI/USR rejection returns to analysis with a required correction reason.
- [x] REQ approval requires the business-owner or explicit same-org delegate relationship plus permission.
- [ ] Initial subtype field templates and visibility are confirmed.
- [x] Parent closure is blocked while any child is non-terminal.
- [ ] Constitution Principle VI amendment is approved and applied.
- [ ] Mentor approves final transition/actor matrix.

**Readiness**: Specification is suitable for review and estimation, but not yet
ready for implementation because the unchecked decisions affect schema,
authorization, and workflow behavior.
