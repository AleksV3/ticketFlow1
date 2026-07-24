# Threat Model: Priority Fixes and UX Improvements

**Reviewed**: 2026-07-24

**Security boundary**: authenticated user, party, current organization scope,
fixed permissions, ticket relationships, and server-owned configuration. UI
visibility and client-supplied IDs are never authorization evidence.

## Assets

- Ticket state, approval decisions, status history, and audit attribution.
- Role-to-permission assignments and the fixed permission catalog.
- Dynamic field definitions and potentially sensitive field values.
- User dashboard, theme, filter, and last-viewed-team preferences.
- Workflow graph identity and connected transitions.
- Tenant membership, users, teams, and organization configuration.

## Actors

- Normal assigned approver or required team leader.
- User with `APPROVE_ALL_TICKETS`.
- Role/field/workflow administrator.
- Ordinary authenticated internal or client user.
- Cross-tenant authenticated attacker.
- Concurrent/stale browser session.

## Threats and required mitigations

| Threat | Attack | Impact | Required mitigation | Verification |
|---|---|---|---|---|
| Global approval bypasses tenant scope | Present a ticket key from another organization while holding `APPROVE_ALL_TICKETS` | Unauthorized business decision and data disclosure | Resolve tenant-visible ticket first; evaluate override only after scope, current-state, and protected-edge checks | global approver succeeds in scope; cross-tenant request is `404` and creates no decision/history/audit |
| Role-name privilege escalation | Rename/create a role called Admin | Gains approval or administration authority | Check fixed permission keys only; never branch on role names | renamed/custom roles behave solely from permission set |
| Approval relationship spoofing | Submit approver/team/user IDs in decision request | Decide a ticket assigned to someone else | Derive approver/team relationship from locked server-side ticket/approval data; request carries only reason/version | manipulated IDs are ignored/rejected; unrelated actor gets `403` |
| Stale/double decision | Two eligible actors approve/reject simultaneously | Conflicting state and duplicate evidence | Lock/version active approval and ticket; unique active invariant; decision and transition in one transaction | one request succeeds, loser gets `409`, one decision/history transition exists |
| Partial approval commit | Audit, decision, comment, or state write fails | State without evidence or evidence without state | One Spring transaction; propagate persistence failures | injected audit/decision failure rolls back ticket, decision, history, comment |
| Generic transition bypass | Call `/transition` directly for protected edge | Avoid approval relationship checks | Filter protected operation kinds from generic command and execution paths | protected target absent from `allowedTransitions`; direct transition returns `409` |
| Field-value disclosure | Read restricted field through detail, list, creation reference, export, audit, or error | Sensitive business/infrastructure data leak | Shared server-side field authorization/projection policy on every response; do not log values | unauthorized response-surface matrix asserts field key/value absent |
| Direct restricted-field mutation | Send hidden field key to create/update endpoint | Unauthorized data modification | Load active definitions and current role grants server-side; authorize each value before any write | direct and mixed authorized/unauthorized payload rolls back fully |
| Field grant fail-open | Delete/deactivate last referenced role or supply empty rule accidentally | Previously restricted field becomes public | Distinguish “never configured” legacy allow from “configured with no grants” deny state; safe role deletion/deactivation rules | role removal cannot turn configured restriction into legacy allow |
| Multi-role ambiguity | Combine one allowing role with another denying/omitting role | Inconsistent authorization | Explicit additive allow policy across active in-scope roles; tenant boundary remains mandatory | multi-role union tests for VIEW/EDIT/CREATE |
| Role mass assignment | Submit unknown, cross-scope, duplicate, or unbounded permission IDs | Privilege escalation, partial mapping, denial of service | Resolve all IDs from fixed catalog, normalize set, validate scope, bound request bytes rather than permission count, replace transactionally | unknown/cross-scope payload rejected; full catalog succeeds; no partial writes |
| Lost role update | Two admins edit from stale pages | Silently removes permissions | Optimistic version/ETag and `409` | concurrent update test preserves winner's complete set |
| Preference horizontal access | Supply another user's ID or organization | Observe/change another user's workspace | User ID comes only from principal; organization from current authenticated context; composite uniqueness | two-user/two-org isolation and manipulated-body tests |
| Preference reference abuse | Store unassigned team, arbitrary widget/filter key, CSS/script content | Data probing, stored injection, broken UI | Developer-owned enum catalogs, membership validation, scalar IDs, JSON serialization/output encoding, payload size bounds | invalid keys/team/script-like values rejected and never rendered |
| Workflow rename graph corruption | Rename using stale state or duplicate name | Broken transitions or misleading workflow | Update display name in place by stable ID, validate uniqueness, optimistic version, configuration audit | connected-edge traversal still works; stale/duplicate rename rejected |
| CSRF on mutations | Malicious site posts approve/role/preference/field changes with auth cookie | Unauthorized state change | Existing CSRF protection on every state-changing browser endpoint | mutation without CSRF token is rejected |
| Sensitive logging | Reasons or restricted fields appear in application/security logs | Confidentiality loss | Log IDs/error codes, not dynamic values or decision reasons; audit stores bounded metadata with correct visibility | log/audit review and tests for internal/public visibility |

## Authorization order

Approval commands must evaluate in this order to avoid tenant leaks:

1. authenticate and validate CSRF;
2. resolve ticket in the caller's visible scope (`404` otherwise);
3. lock/load active approval and current workflow state (`409` if stale);
4. validate fixed required permission and party;
5. validate explicit approver, required team leader, or global permission;
6. execute decision, transition, history, reason, and audit atomically.

Field reads and writes must resolve the ticket/field in tenant scope before
evaluating role grants. Preferences never accept a target user ID from the
client.

## Residual risks

- A legitimately powerful global approver can make a wrong business decision.
  Audit attribution, least-privilege role assignment, and visible permission
  descriptions mitigate but cannot eliminate this operational risk.
- Legacy fields default to current visibility until grants are configured.
  Administrators must review sensitive existing fields during rollout.
- Bounded JSON preferences can become incompatible with future catalog changes;
  reads must safely discard retired keys and fall back to defaults.

## Release security gate

- Approval actor/state/tenant/concurrency matrix passes.
- Direct API field attacks and response-leakage matrix pass.
- Role full-catalog, unknown-ID, duplicate, stale-write, and authorization tests
  pass.
- Preference cross-user/cross-organization tests pass.
- CSRF checks cover every new mutation.
- Logs and audit feeds contain no restricted field values or incorrectly
  visible decision reasons.
