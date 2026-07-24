# Feature 003 API Contracts

These contracts extend feature `001` and `002` contracts. Existing response
fields and endpoints remain compatible unless explicitly changed here.

## Error semantics

All errors use the existing `ApiError` envelope.

- `400 VALIDATION_FAILED`: malformed body, unknown catalog ID, duplicate or
  invalid field rule, blank/duplicate workflow-state name.
- `403 FORBIDDEN`: authenticated actor can see the resource but lacks the
  permission or approval/field relationship required for the operation.
- `404 NOT_FOUND`: resource does not exist in the caller's tenant-visible scope.
- `409 CONFLICT`: stale version, already-decided approval, invalid current
  workflow state, or membership/configuration changed since load.

## Contract index

- [Approvals](approvals.md)
- [Roles](roles.md)
- [Preferences](preferences.md)
- [Field grants](field-grants.md)
- [Ticket filters](filters.md)
- [Workflow state rename](workflows.md)
