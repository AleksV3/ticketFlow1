# Workflow State Rename Contract

Rename a persisted workflow state in place:

`PATCH /api/admin/workflows/{workflowId}/states/{stateId}`

```json
{ "name": "Awaiting Security Approval", "version": 5 }
```

The stable state ID and key remain unchanged; all incoming/outgoing transitions
therefore remain connected. Blank or duplicate display names return `400`.
Out-of-scope resources return `404`, missing management authority returns `403`,
and stale versions return `409`. A successful rename returns the updated state
and writes configuration audit with old/new names.
