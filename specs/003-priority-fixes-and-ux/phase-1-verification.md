# Phase 1 Verification: TASI Approval

**Date**: 2026-07-24

## Completed checks

- Backend production compilation: passed with `./mvnw -DskipTests package`.
- `TicketTransitionServiceTest`: 31 focused tests passed.
- `workflow-ui.test.tsx`: 3 focused frontend tests passed.
- `git diff --check`: passed at each checkpoint.
- The original red team-lead regression is now green.

The automated matrix covers:

- explicit assigned approver;
- assigned team-lead fallback;
- unrelated internal actor denial;
- inactive approver command omission;
- protected command exposure;
- stale/already-decided conflict;
- decision record, approval closure, status history, and audit assertions.

`TicketControllerIntegrationTest.tasiApproval_enforcesActorCommandsEvidenceStaleStateAndRollback`
adds the database/API matrix for:

- approve and reject happy paths;
- server-driven workflow commands;
- unrelated `403`;
- cross-scope `404`;
- inactive approver denial;
- stale repeat `409`;
- exactly one approval and decision;
- attributable approval audit;
- forced audit failure rolling back ticket, approval, decision, and history.

## Integration and UI execution

Docker became available and the targeted Testcontainers test was rerun on
2026-07-24. It passed against a fresh PostgreSQL 16 container after Flyway
successfully validated and applied all 28 migrations:

```text
TicketControllerIntegrationTest
#tasiApproval_enforcesActorCommandsEvidenceStaleStateAndRollback
Tests run: 1, Failures: 0, Errors: 0
```

The focused Playwright browser walkthrough also passed:

```text
TASI routing, approval, and subticket creation flow
1 passed
```

The focused Vitest UI suite remained green:

```text
Test Files: 1 passed
Tests: 3 passed
```

The Playwright development server reported pre-existing duplicate React-key
and `ResizeObserver` console warnings, but the approval walkthrough completed
successfully. Those warnings are not approval failures and remain candidates
for the later UI quality phases.

T013 is complete.
