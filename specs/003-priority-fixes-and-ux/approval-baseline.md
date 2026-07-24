# TASI Approval Baseline and Reproduction

**Captured**: 2026-07-24

**Scope**: Current implementation before feature `003` production changes.

## Current flow

1. `POST /api/tickets` loads the active subtype routing rule.
2. `TicketService.applyRouting` copies the rule's team, primary developer, and
   explicit `approver_id` to the ticket as `resolved_approver_id`.
3. A standard transition moves `ANALYSIS` to `PENDING_APPROVAL`.
4. No approval record is created on entry to `PENDING_APPROVAL`.
5. Ticket detail derives `workflowCommands` from protected outgoing workflow
   edges and `TicketTransitionService.isAllowed`.
6. `isAllowed` permits TASI approve/reject only when the actor has the edge
   permission, is TicketFlow1 party, and exactly matches
   `ticket.resolved_approver_id`.
7. The protected endpoint changes state, writes status history and generic
   `STATUS_CHANGED` audit, and may add a reason comment.
8. Although `TicketDecision` and `TicketDecisionRepository` exist,
   `TicketTransitionService` does not create a decision record.

## Reproduction

The regression test
`TicketTransitionServiceTest.workflowApproval_allowsAssignedTeamLeaderWhenNoExplicitApprover`
models a TASI in `PENDING_APPROVAL` routed to a team whose leader is the actor,
with no explicit resolved approver.

Expected baseline behavior:

- the team leader is an eligible workflow approver;
- approval moves the ticket to `IMPLEMENTATION`.

Observed current behavior:

- `isAllowed` ignores the assigned team's leader;
- `transitionOwned` throws `IllegalTransitionException`;
- ticket detail would omit `WORKFLOW_APPROVE` and `WORKFLOW_REJECT`;
- the same actor sees no button and cannot successfully call the endpoint.

Run only the reproducer:

```bash
cd backend
./mvnw -Dtest=TicketTransitionServiceTest#workflowApproval_allowsAssignedTeamLeaderWhenNoExplicitApprover test
```

The test is intentionally red in Phase 0. Phase 1 must turn it green without
weakening tenant, party, permission, active-state, or protected-edge checks.

## Root-cause statement

The immediate “nobody can approve” path is caused by incomplete approver
resolution: routing supports an optional explicit approver, but authorization
does not fall back to the assigned team leader when that field is absent.

The investigation also found two related correctness gaps that Phase 1 must
address:

1. entering the approval state does not create an active approval/domain record;
2. approve/reject does not persist `TicketDecision`, despite the schema and
   repository already existing.

The existing happy-path integration test masks approver resolution by directly
updating `ticket.resolved_approver_id` in SQL before executing the workflow.
That test proves the state edge can run, but not that production routing and
approver resolution work end to end.

## Constraints for the fix

- Do not expose protected approval edges through the generic transition API.
- Resolve explicit approver first, then the configured team-lead rule.
- Re-check authorization at command execution; frontend commands are advisory.
- Persist approval/decision, state transition, status history, and audit in one
  transaction.
- Return `403` for an in-scope actor who lacks decision authority and `409` for
  stale/current-state conflicts.
- Preserve tenant non-disclosure (`404`) where applicable.
