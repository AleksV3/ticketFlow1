# Role Contracts

Role create/update uses an authoritative set of distinct fixed-catalog
permission IDs. No application count limit applies.

```json
{
  "name": "Operations Approver",
  "party": "TICKETFLOW1",
  "permissionIds": [1, 2, 3, 17],
  "version": 4
}
```

Successful update replaces the permission mapping transactionally and returns
the complete persisted set plus a new version. Omitted existing IDs are removed;
included existing IDs are retained. Duplicate IDs are normalized to one mapping
or rejected with `400`, consistently across create and update. Unknown or
out-of-scope IDs return `400` without partial writes. A stale version returns
`409`; unauthorized administration returns `403`.
