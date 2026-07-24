# Ticket Filter Contract

`GET /api/tickets` retains existing query parameters and adds only missing
supported filters:

```text
q, type, status, priority, assigneeId, teamId, creatorId,
createdFrom, createdTo, workflowId, approvalStatus
```

The frontend maintains draft controls locally. It sends a request only on
initial load, text-search submission/debounce under the existing policy,
`Apply filters`, or `Clear filters`. Invalid IDs, enums, or date ranges return
`400`; out-of-scope references return an empty result or `404` according to
existing tenant non-disclosure rules.
