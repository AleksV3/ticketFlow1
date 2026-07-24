# Dynamic Field Grant Contracts

Field administration responses and mutations add role grants:

```json
{
  "fieldId": 80,
  "viewRoleIds": [4, 5],
  "editRoleIds": [5],
  "createRoleIds": [4, 5],
  "version": 2
}
```

Zero configured grants across all operations preserves legacy visibility.
Once configured, authorization is additive across the actor's active roles.
Role IDs must be valid in the field configuration scope.

Unauthorized fields are omitted from creation references and ticket response
values. Direct create/update attempts return `403` (or a field-addressed
`400` when non-disclosure requires it) and never partially update other field
values. Unknown/cross-scope roles return `400`; stale field configuration
returns `409`; unauthorized administration returns `403`.
