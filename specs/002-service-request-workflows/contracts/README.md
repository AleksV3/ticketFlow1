# Service Request Workflow API Contracts

These files supplement the generated Swagger/OpenAPI UI exposed by the backend
at `/swagger-ui.html`.

- [`tickets.md`](tickets.md) documents the ticket creation, subtype,
  dynamic-value, target-user, parent-ticket, workflow-command, and list/detail
  response extensions for TASI, USR, DFCT, and REQ.
- [`admin.md`](admin.md) documents the runtime configuration API for subtypes,
  dynamic fields, options, routing rules, and type availability.

The implementation source of truth is the Spring controller layer:

- `TicketController`
- `ReferenceController`
- `TicketConfigurationController`
- `WorkflowAdminController`

Protected workflow operations are intentionally separate from generic
transition execution. They are returned as `workflowCommands` and executed by
dedicated endpoints such as `/workflow-approve`, `/workflow-reject`,
`/client-accept`, `/client-reject`, and `/correction-return`.
