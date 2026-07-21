# Threat Model

The primary trust boundaries are browser/API input, CLIENT versus TICKETFLOW1,
organization tenancy, administrator configuration, and protected workflow
decisions. Every mitigation below requires backend enforcement and tests.

| Threat | Attack | Required mitigation and evidence |
|---|---|---|
| Tenant data leak | Substitute organization, subtype, parent, user, team, option, or routing IDs | Derive client org from principal; join ownership in repository/service reads; return `404` for foreign IDs; two-org integration tests |
| User enumeration | Probe autocomplete with short terms, emails, foreign org IDs, or timing/count differences | Minimum 2 chars, 20-result cap, active users only, caller-derived scope, uniform foreign/not-found response, no total count, rate-limit-ready endpoint tests |
| Dynamic-value injection | Submit unknown keys, inactive options, oversized text, HTML/script, SQL-like content, wrong typed values | Server-loaded allowlisted definitions; reject unknowns; bounded plain-text storage; typed columns/FKs; output encoding; validation tests |
| Mass assignment | Send approver, routing rule, developer, internal visibility, state, capability, or parent organization in create/update DTO | Explicit DTO allowlists; routing and scope resolved server-side; override requires `TICKET_ASSIGN`; reject status in generic patch; manipulated-ID tests |
| Approval bypass | Call generic transition, replay decision, use generic permission, self-approve, or approve another team's ticket | Protected operation kinds excluded from generic endpoint; dedicated transactional command; permission + party + relationship + current-state checks; optimistic locking; matrix tests |
| CSRF | Cross-site state-changing requests using auth cookie | Existing CSRF header/cookie mechanism on every mutation; same-site secure production cookie; integration coverage for missing/invalid token |
| Cyclic/cross-scope parents | Self-parent or construct indirect/cross-tenant cycle | Lock/load visible ancestry in transaction; same-scope invariant; max traversal guard; self/ancestor tests; FK `RESTRICT` |
| Unsafe configuration deletion | Delete used subtype/field/option/type or race deletion with create | Reference checks plus FK `RESTRICT`; deactivate used records; optimistic version; transactional conflict response; concurrent/reference tests |
| Routing privilege escalation | Configure inactive/foreign team/user, non-member developer, or arbitrary approver | Internal party + `TYPE_MANAGE`; active/scope/team-membership validation; explicit leader/designated approver; configuration audit; cross-scope tests |
| Internal-data disclosure | Return internal definitions/values/reasons/audit to client or realtime payload | Visibility filtering in service DTO mapping and audit/realtime projection; never serialize entities; client snapshot tests |
| Historical ambiguity | Rename/delete users/options so old ticket meaning changes | Store immutable IDs plus display snapshots where needed; deactivate referenced config; render historical label safely |
| Resource exhaustion | Configure huge forms/options or submit giant/many values; deep parent chain | 50 fields/subtype, 100 options/field, bounded strings/results/pages, request-size limits, ancestry traversal guard |

## Security completion conditions

- No controller relies on frontend hiding or role names.
- Every new mutation has authorization, tenant-scope, validation, audit, and
  rollback tests proportional to the path.
- Logs and audit metadata use safe identifiers/labels, not sensitive field
  bodies or target-user details.
- SQL remains parameterized through JPA/repositories or fixed migrations; admin
  input never becomes SQL, HTML, scripts, expressions, or permission keys.
- Realtime invalidation messages contain ticket IDs/versions only and each
  subscriber refetches through an authorized endpoint.

