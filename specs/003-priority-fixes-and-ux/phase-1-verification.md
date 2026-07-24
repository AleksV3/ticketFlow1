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

## Environment-blocked check

The targeted Testcontainers integration test was attempted both inside the
sandbox and with an escalation request. This host does not expose a usable
Docker socket:

```text
DOCKER_HOST unix:///var/run/docker.sock is not listening
Could not find a valid Docker environment
```

No local backend/PostgreSQL socket is accessible from the execution
environment. Therefore T013 remains unchecked: the integration test is written
and compiled, but the complete API/UI/database walkthrough has not been
executed here.

## Command to finish T013

Run on a host with Docker access:

```bash
cd backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
./mvnw -Dtest=TicketControllerIntegrationTest#tasiApproval_enforcesActorCommandsEvidenceStaleStateAndRollback test
```

Then run the frontend focused check:

```bash
cd frontend
npm test -- --run test/workflow-ui.test.tsx
```

T013 may be checked only after the Testcontainers command passes.
